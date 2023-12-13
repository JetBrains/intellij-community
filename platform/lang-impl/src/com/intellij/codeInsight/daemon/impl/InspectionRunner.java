// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.concurrency.JobLauncher;
import com.intellij.concurrency.JobLauncherImpl;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.TextRangeScalarUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.*;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSetQueue;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class InspectionRunner {
  private final PsiFile myPsiFile;
  private final TextRange myRestrictRange;
  private final TextRange myPriorityRange;
  private final boolean myInspectInjected;
  private final boolean myIsOnTheFly;
  private final ProgressIndicator myProgress;
  private final boolean myIgnoreSuppressed;
  private final InspectionProfileWrapper myInspectionProfileWrapper;
  private final Map<String, Set<PsiElement>> mySuppressedElements;

  InspectionRunner(@NotNull PsiFile psiFile,
                   @NotNull TextRange restrictRange,
                   @NotNull TextRange priorityRange,
                   boolean inspectInjected,
                   boolean isOnTheFly,
                   @NotNull ProgressIndicator progress,
                   boolean ignoreSuppressed,
                   @NotNull InspectionProfileWrapper inspectionProfileWrapper,
                   @NotNull Map<String, Set<PsiElement>> suppressedElements) {
    myPsiFile = psiFile;
    myRestrictRange = restrictRange;
    myPriorityRange = priorityRange;
    myInspectInjected = inspectInjected;
    myIsOnTheFly = isOnTheFly;
    myProgress = progress;
    myIgnoreSuppressed = ignoreSuppressed;
    myInspectionProfileWrapper = inspectionProfileWrapper;
    mySuppressedElements = suppressedElements;
  }

  record InspectionContext(@NotNull LocalInspectionToolWrapper tool,
                           @NotNull InspectionProblemHolder holder,
                           @NotNull PsiElementVisitor visitor,
                           @NotNull List<? extends PsiElement> elements,
                           boolean isVisible,
                           @NotNull List<Class<?>> acceptingPsiTypes,
                           // the containing file this tool was called for. In the case of injected context, this will be the injected file.
                           @NotNull PsiFile psiFile) {
    @Override
    public String toString() {
      return tool +"; elements:"+elements.size();
    }
  }

  @NotNull List<? extends InspectionContext> inspect(@NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                                                     @Nullable HighlightSeverity minimumSeverity,
                                                     boolean addRedundantSuppressions,
                                                     @NotNull BiConsumer<? super ProblemDescriptor, ? super InspectionProblemHolder> applyIncrementallyCallback,
                                                     @NotNull Consumer<? super InspectionContext> contextFinishedCallback,
                                                     @Nullable Condition<? super LocalInspectionToolWrapper> enabledToolsPredicate) {
    if (!shouldInspect(myPsiFile)) {
      return Collections.emptyList();
    }
    List<Divider.DividedElements> allDivided = new ArrayList<>();
    Divider.divideInsideAndOutsideAllRoots(myPsiFile, myRestrictRange, myPriorityRange,
                                           file -> shouldInspect(file), new CommonProcessors.CollectProcessor<>(allDivided));
    List<PsiElement> inside = ContainerUtil.concat(ContainerUtil.map(allDivided, d -> d.inside()));
    // might be different from myPriorityRange because DividedElements can cache not exact but containing ranges
    long finalPriorityRange = finalPriorityRange(myPriorityRange, allDivided);
    List<PsiElement> outside = ContainerUtil.concat(ContainerUtil.map(allDivided, d -> ContainerUtil.concat(d.outside(), d.parents())));
    List<InspectionContext> injectedContexts = Collections.synchronizedList(new ArrayList<>());

    List<LocalInspectionToolWrapper> applicableByLanguage =
      InspectionEngine.filterToolsApplicableByLanguage(toolWrappers, InspectionEngine.calcElementDialectIds(inside, outside));

    List<InspectionContext> init = new ArrayList<>(applicableByLanguage.size()*2); // tools for 'inside' + tools for 'outside'
    List<InspectionContext> invisibleContexts = new ArrayList<>(applicableByLanguage.size()*2); // tools for 'inside' + tools for 'outside'
    List<InspectionContext> redundantContexts = new ArrayList<>();
    InspectionEngine.withSession(myPsiFile, myRestrictRange, TextRangeScalarUtil.create(finalPriorityRange), minimumSeverity, myIsOnTheFly, session -> {
      for (LocalInspectionToolWrapper toolWrapper : applicableByLanguage) {
        if (enabledToolsPredicate == null || enabledToolsPredicate.value(toolWrapper)) {
          LocalInspectionTool tool = toolWrapper.getTool();
          InspectionProblemHolder holder = new InspectionProblemHolder(myPsiFile, toolWrapper, myIsOnTheFly, myInspectionProfileWrapper, applyIncrementallyCallback);
          PsiElementVisitor visitor = InspectionEngine.createVisitor(tool, holder, myIsOnTheFly, session);
          // if inspection returned the empty visitor, then it should be skipped
          if (visitor == PsiElementVisitor.EMPTY_VISITOR) {
            continue;
          }
          tool.inspectionStarted(session, myIsOnTheFly);

          List<Class<?>> acceptingPsiTypes = InspectionVisitorsOptimizer.getAcceptingPsiTypes(visitor);
          init.add(new InspectionContext(toolWrapper, holder, visitor, inside, true, acceptingPsiTypes, myPsiFile));
          if (toolWrapper.getID().equals("SSBasedInspection")) {
            // todo remove when SSBasedVisitor is thread-safe, see https://youtrack.jetbrains.com/issue/IDEA-335546
            visitor = InspectionEngine.createVisitor(tool, holder, myIsOnTheFly, session);
          }
          invisibleContexts.add(new InspectionContext(toolWrapper, holder, visitor, outside, false, acceptingPsiTypes, myPsiFile));
        }
      }
      //sort `init`, according to the priorities, saved earlier to run in order
      // but only for visible elements, because we don't care about the order in 'outside', and spending CPU on their rearrangement would be counterproductive
      InspectionProfilerDataHolder profileData = InspectionProfilerDataHolder.getInstance(myPsiFile.getProject());
      profileData.sortAndRetrieveFavoriteElement(myPsiFile, init);

      long start = System.nanoTime();
      // injected -> host
      InspectionContext TOMB_STONE = createTombStone();
      Consumer<InspectionContext> onComplete = context -> {
        // an inspection is considered finished only when it visited both 'inside' and 'outside' elements
        InspectionProblemHolder holder = context.holder;
        if (holder.toolWasProcessed.incrementAndGet() == 2) {
          holder.finishTimeStamp = System.nanoTime();
          context.tool.getTool().inspectionFinished(session, holder);
          contextFinishedCallback.accept(context);
        }
      };

      List<InspectionContext> allContexts = ContainerUtil.concat(init, invisibleContexts);
      // have to do all this even for empty elements, in order to perform correct cleanup/inspectionFinished
      BlockingQueue<InspectionContext> contexts = new ArrayBlockingQueue<>(allContexts.size() + 1, false, allContexts);
      boolean added = contexts.offer(TOMB_STONE);
      assert added;
      ForkJoinTask<Boolean> regularElementsFuture = processInOrderAsync(contexts, TOMB_STONE,
          context -> processContext(finalPriorityRange, myRestrictRange, context, onComplete));

      ForkJoinTask<Boolean> injectedElementsFuture = null;
      if (myInspectInjected && InjectionUtils.shouldInspectInjectedFiles(myPsiFile)) {
        BlockingQueue<Pair<PsiFile, PsiElement>> queue = new LinkedBlockingQueue<>();
        Pair<PsiFile, PsiElement> tombStone = Pair.create(null, null);
        try {
          injectedElementsFuture =
            startInspectingInjectedPsi(session, queue, toolWrappers, injectedContexts, applyIncrementallyCallback, contextFinishedCallback, enabledToolsPredicate,
                                       tombStone);
          getInjectedWithHosts(ContainerUtil.concat(inside, outside), queue);
        }
        finally {
          try {
            queue.put(tombStone);
          }
          catch (InterruptedException e) {
            throw new ProcessCanceledException(e);
          }
        }
      }

      reportIdsOfInspectionsReportedAnyProblemToFUS(allContexts);

      try {
        // do not call .get() to avoid overcompensation
        if (!regularElementsFuture.invoke() | (injectedElementsFuture != null && !injectedElementsFuture.invoke())) {
          throw new ProcessCanceledException();
        }
      }
      catch (Exception e) {
        ExceptionUtil.rethrow(e);
      }
      boolean isWholeFileInspectionsPass = !init.isEmpty() && init.get(0).tool.runForWholeFile();
      if (myIsOnTheFly && !isWholeFileInspectionsPass) {
        // do not save stats for the batch process, there could be too many files
        profileData.saveStats(myPsiFile, init, System.nanoTime() - start);
      }
      if (myIsOnTheFly && addRedundantSuppressions) {
        addRedundantSuppressions(allContexts, toolWrappers, redundantContexts, applyIncrementallyCallback, contextFinishedCallback);
      }
    });
    return ContainerUtil.concat(init, invisibleContexts, redundantContexts, injectedContexts);
  }

  private void reportIdsOfInspectionsReportedAnyProblemToFUS(@NotNull List<? extends InspectionContext> init) {
    if (!StatisticsUploadAssistant.isCollectAllowedOrForced()) return;
    Set<String> inspectionIdsReportedProblems = new HashSet<>();
    InspectionProblemHolder holder;
    for (InspectionContext context : init) {
      holder = context.holder;
      if (holder.anyProblemWasReported()) {
        inspectionIdsReportedProblems.add(context.tool.getID());
      }
    }
    InspectionUsageFUSStorage.getInstance(myPsiFile.getProject()).reportInspectionsWhichReportedProblems(inspectionIdsReportedProblems);
  }

  private static long finalPriorityRange(@NotNull TextRange priorityRange, @NotNull List<? extends Divider.DividedElements> allDivided) {
    long finalPriorityRange = allDivided.isEmpty() ? TextRangeScalarUtil.toScalarRange(priorityRange) : allDivided.get(0).priorityRange();
    for (int i = 1; i < allDivided.size(); i++) {
      Divider.DividedElements dividedElements = allDivided.get(i);
      finalPriorityRange = TextRangeScalarUtil.union(finalPriorityRange, dividedElements.priorityRange());
    }
    return finalPriorityRange;
  }

  private @NotNull InspectionContext createTombStone() {
    LocalInspectionToolWrapper tool = new LocalInspectionToolWrapper(new LocalInspectionEP());
    InspectionProblemHolder fake = new InspectionProblemHolder(myPsiFile, tool, false, myInspectionProfileWrapper, emptyBiCallback);
    return new InspectionContext(tool, fake, new PsiElementVisitor() {}, List.of(), false, List.of(), myPsiFile);
  }

  private static @NotNull <T> Map<PsiFile, T> createInjectedFileMap() {
    // TODO remove when injected PsiFile implemented equals() base on its offsets in the host
    return CollectionFactory.createCustomHashingStrategyMap(new HashingStrategy<>() {
      @Override
      public int hashCode(PsiFile f) {
        if (f == null) return 0;
        VirtualFile v = f.getVirtualFile();
        VirtualFile host = v instanceof VirtualFileWindow window ? window.getDelegate() : null;
        if (host == null) return 0;
        // host + offset in host
        return v.hashCode() * 37 + Objects.hashCode(ArrayUtil.getFirstElement(((VirtualFileWindow)v).getDocumentWindow().getHostRanges()));
      }

      @Override
      public boolean equals(PsiFile f1, PsiFile f2) {
        if (f1 == null || f2 == null || f1 == f2) {
          return f1 == f2;
        }
        VirtualFile v1 = f1.getVirtualFile();
        VirtualFile v2 = f2.getVirtualFile();
        if (!(v1 instanceof VirtualFileWindow w1) || !(v2 instanceof VirtualFileWindow w2)) {
          return Objects.equals(v1, v2);
        }
        VirtualFile d1 = w1.getDelegate();
        VirtualFile d2 = w2.getDelegate();
        if (!Objects.equals(d1, d2)) {
          return false;
        }
        return Arrays.equals(w1.getDocumentWindow().getHostRanges(), w2.getDocumentWindow().getHostRanges());
      }
    });
  }

  private void registerSuppressedElements(@NotNull PsiElement element, @NotNull String id, @Nullable String alternativeID) {
    mySuppressedElements.computeIfAbsent(id, __ -> new HashSet<>()).add(element);
    if (alternativeID != null && !alternativeID.equals(id)) {
      mySuppressedElements.computeIfAbsent(alternativeID, __ -> new HashSet<>()).add(element);
    }
  }

  private void addRedundantSuppressions(@NotNull List<? extends InspectionContext> init,
                                        @NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                                        @NotNull List<? super InspectionContext> result,
                                        @NotNull BiConsumer<? super ProblemDescriptor, ? super InspectionProblemHolder> applyIncrementallyCallback,
                                        @NotNull Consumer<? super InspectionContext> contextFinishedCallback) {
    for (InspectionContext context : init) {
      LocalInspectionToolWrapper toolWrapper = context.tool;
      LocalInspectionTool tool = toolWrapper.getTool();
      for (ProblemDescriptor descriptor : context.holder.getResults()) {
        PsiElement element = descriptor.getPsiElement();
        if (element != null && tool.isSuppressedFor(element)) {
          registerSuppressedElements(element, toolWrapper.getID(), toolWrapper.getAlternativeID());
        }
      }
    }
    HighlightDisplayKey redundantSuppressionKey = HighlightDisplayKey.find(RedundantSuppressInspectionBase.SHORT_NAME);
    InspectionProfile inspectionProfile = myInspectionProfileWrapper.getInspectionProfile();
    if (redundantSuppressionKey == null || !inspectionProfile.isToolEnabled(redundantSuppressionKey, myPsiFile)) {
      return;
    }
    Language fileLanguage = myPsiFile.getLanguage();
    InspectionSuppressor suppressor = ContainerUtil.find(LanguageInspectionSuppressors.INSTANCE.allForLanguage(fileLanguage), s -> s instanceof RedundantSuppressionDetector);
    if (!(suppressor instanceof RedundantSuppressionDetector redundantSuppressionDetector)) {
      return;
    }
    Set<String> activeTools = new HashSet<>();
    for (LocalInspectionToolWrapper tool : toolWrappers) {
      if (tool.runForWholeFile()) {
        // no redundants for whole file tools pass
        return;
      }
      if (tool.isUnfair() || !tool.isApplicable(fileLanguage) || myInspectionProfileWrapper.getInspectionTool(tool.getShortName(), myPsiFile) instanceof GlobalInspectionToolWrapper) {
        continue;
      }
      activeTools.add(tool.getID());
      ContainerUtil.addIfNotNull(activeTools, tool.getAlternativeID());
      InspectionElementsMerger elementsMerger = InspectionElementsMerger.getMerger(tool.getShortName());
      if (elementsMerger != null) {
        activeTools.addAll(Arrays.asList(elementsMerger.getSuppressIds()));
      }
    }
    InspectionToolWrapper<?,?> redundantSuppressTool = inspectionProfile.getInspectionTool(RedundantSuppressInspectionBase.SHORT_NAME, myPsiFile);
    LocalInspectionTool rsLocalTool = ((RedundantSuppressInspectionBase)redundantSuppressTool.getTool()).createLocalTool(redundantSuppressionDetector, mySuppressedElements, activeTools);
    List<LocalInspectionToolWrapper> wrappers = Collections.singletonList(new LocalInspectionToolWrapper(rsLocalTool));
    InspectionRunner runner = new InspectionRunner(myPsiFile, myRestrictRange, myPriorityRange, myInspectInjected, true, myProgress, false,
                                                   myInspectionProfileWrapper, mySuppressedElements);
    result.addAll(runner.inspect(wrappers, HighlightSeverity.WARNING, false, applyIncrementallyCallback, contextFinishedCallback, null));
  }

  private static final @NotNull BiConsumer<ProblemDescriptor, InspectionProblemHolder> emptyBiCallback = (__,___) -> { };

  private <T> ForkJoinTask<Boolean> processInOrderAsync(@NotNull BlockingQueue<T> contexts,
                                                        @NotNull T TOMB_STONE,
                                                        @NotNull Consumer<? super T> processor) {
    ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    boolean shouldFailFastAcquiringReadAction = application.isInImpatientReader();

    return
      ((JobLauncherImpl)JobLauncher.getInstance()).processQueueAsync(contexts, new SensitiveProgressWrapper(myProgress),
                                                                     TOMB_STONE, context ->
        AstLoadingFilter.disallowTreeLoading(() -> AstLoadingFilter.<Boolean, RuntimeException>forceAllowTreeLoading(myPsiFile, () -> {
          Runnable runnable = () -> {
            if (!application.tryRunReadAction(() -> processor.accept(context))) {
              throw new ProcessCanceledException();
            }
          };

          if (shouldFailFastAcquiringReadAction) {
            application.executeByImpatientReader(runnable);
          }
          else {
            runnable.run();
          }
          return true;
        }))
      );
  }

  /**
   * for each tool in {@code init} (in this order) call its {@link InspectionContext#visitor} on every PsiElement in {@code inside} list (starting from this inspection's {@link InspectionProblemHolder#myFavoriteElement} if any),
   * maintaining parallelism during this process (i.e., several visitors from {@code init} can be executed concurrently, but elements from the list head get higher priority than the list tail).
   */
  private static void processContext(long priorityRange,
                                     @NotNull TextRange restrictRange,
                                     @NotNull InspectionContext context,
                                     @NotNull Consumer<? super InspectionContext> afterProcessCallback) {
    List<? extends PsiElement> elements = context.elements();
    Map<Class<?>, Collection<Class<?>>> targetPsiClasses = InspectionVisitorsOptimizer.getTargetPsiClasses(elements);
    InspectionProblemHolder holder = context.holder;
    PsiElement oldFavoriteElement = holder.myFavoriteElement;

    // accept favoriteElement only if it belongs to the correct inside/outside list
    Document document = context.psiFile.getViewProvider().getDocument();
    if (oldFavoriteElement != null &&
        context.isVisible() == TextRangeScalarUtil.intersects(oldFavoriteElement.getTextRange(), priorityRange) &&
        restrictRange.contains(oldFavoriteElement.getTextRange())) {
      holder.myFavoriteElement = null; // null the element to make sure it will hold the new favorite after this method finished
      // run first for the element we know resulted in the diagnostics during the previous run
      holder.visitElement(oldFavoriteElement, context.visitor, document);
    }

    if (context.acceptingPsiTypes == InspectionVisitorsOptimizer.ALL_ELEMENTS_VISIT_LIST) {
      for (int i = 0; i < elements.size(); i++) {
        PsiElement element = elements.get(i);

        if (element == oldFavoriteElement) continue; // already visited

        ProgressManager.checkCanceled();
        holder.visitElement(element, context.visitor, document);
      }
    }
    else {
      Set<Class<?>> accepts = InspectionVisitorsOptimizer.getVisitorAcceptClasses(targetPsiClasses, context.acceptingPsiTypes);
      if (accepts != null && !accepts.isEmpty()) {
        for (int i = 0; i < elements.size(); i++) {
          PsiElement element = elements.get(i);
          if (element == oldFavoriteElement) continue; // already visited
          if (accepts.contains(element.getClass())) {
            ProgressManager.checkCanceled();
            holder.visitElement(element, context.visitor, document);
          }
        }
      }
    }
    afterProcessCallback.accept(context);
  }

  private ForkJoinTask<Boolean> startInspectingInjectedPsi(@NotNull LocalInspectionToolSession session,
                                               @NotNull BlockingQueue<Pair<PsiFile, PsiElement>> injectedFilesWithHosts,
                                               @NotNull List<? extends LocalInspectionToolWrapper> wrappers,
                                               @NotNull List<? super InspectionContext> outInjectedContexts,
                                               @NotNull BiConsumer<? super ProblemDescriptor, ? super InspectionProblemHolder> addDescriptorIncrementallyCallback,
                                               @NotNull Consumer<? super InspectionContext> contextFinishedCallback,
                                               @Nullable Condition<? super LocalInspectionToolWrapper> enabledToolsPredicate,
                                               @NotNull Pair<PsiFile, PsiElement> tombStone) {
    return processInOrderAsync(injectedFilesWithHosts, tombStone, pair -> {
      PsiFile injectedPsi = pair.getFirst();
      if (!shouldInspect(injectedPsi)) {
        return;
      }
      PsiElement host = pair.getSecond();
      BiConsumer<? super ProblemDescriptor, ? super InspectionProblemHolder> applyIncrementallyCallback = (descriptor, holder) -> {
        PsiElement descriptorPsiElement = descriptor.getPsiElement();
        LocalInspectionToolWrapper wrapper = holder.myToolWrapper;
        if (myIgnoreSuppressed &&
            (wrapper.getTool().isSuppressedFor(host)
             || descriptorPsiElement != null && wrapper.getTool().isSuppressedFor(descriptorPsiElement))) {
          registerSuppressedElements(host, wrapper.getID(), wrapper.getAlternativeID());
          return/* false*/;
        }
        if (myIsOnTheFly) {
          addDescriptorIncrementallyCallback.accept(descriptor, holder);
        }
      };
      // convert host priority range to the injected
      Document document = PsiDocumentManager.getInstance(myPsiFile.getProject()).getDocument(injectedPsi);
      TextRange injectedPriorityRange;
      if (document instanceof DocumentWindow documentWindow) {
        int start = documentWindow.hostToInjected(myPriorityRange.getStartOffset());
        int end = documentWindow.hostToInjected(myPriorityRange.getEndOffset());
        injectedPriorityRange = TextRange.isProperRange(start, end) ? new TextRange(start, end) : TextRange.EMPTY_RANGE;
      }
      else {
        injectedPriorityRange = TextRange.EMPTY_RANGE;
      }
      InspectionRunner injectedRunner = new InspectionRunner(injectedPsi, injectedPsi.getTextRange(),
                                                             injectedPriorityRange, false, myIsOnTheFly, myProgress,
                                                             myIgnoreSuppressed, myInspectionProfileWrapper, mySuppressedElements);
      List<? extends InspectionContext> injectedContexts = injectedRunner.inspect(
        wrappers, session.getMinimumSeverity(), true, applyIncrementallyCallback,
        contextFinishedCallback, enabledToolsPredicate);
      outInjectedContexts.addAll(injectedContexts);
    });
  }

  private void getInjectedWithHosts(@NotNull List<? extends PsiElement> elements, @NotNull BlockingQueue<? super Pair<PsiFile, PsiElement>> result) {
    Map<PsiFile, PsiElement> injectedToHost = createInjectedFileMap();
    Project project = myPsiFile.getProject();
    for (PsiElement element : elements) {
      InjectedLanguageManager.getInstance(project).enumerateEx(element, myPsiFile, false,
                                                               (injectedPsi, __) -> {
                                                                 if (injectedToHost.put(injectedPsi, element) == null) {
                                                                   try {
                                                                     result.put(Pair.create(injectedPsi, element));
                                                                   }
                                                                   catch (InterruptedException e) {
                                                                     throw new ProcessCanceledException(e);
                                                                   }
                                                                 }
                                                               });
    }
  }

  static final class InspectionProblemHolder extends ProblemsHolder {
    final @NotNull LocalInspectionToolWrapper myToolWrapper;
    private final InspectionProfileWrapper myProfileWrapper;
    @NotNull
    private final BiConsumer<? super ProblemDescriptor, ? super InspectionProblemHolder> applyIncrementallyCallback;
    private final Iterator<HighlightInfo> infoIterator; // iterator into toolInfos, guarded by toolInfos
    long errorStamp; // nano-stamp of the first error created
    long warningStamp; // nano-stamp of the first warning created
    long otherStamp; // nano-stamp of the first info/weak warn/etc. created
    final long initTimeStamp = System.nanoTime();
    volatile long finishTimeStamp;
    volatile PsiElement myFavoriteElement; // the element during visiting which, some diagnostics were generated in the previous run
    @NotNull final AtomicInteger toolWasProcessed = new AtomicInteger();
    // has to ignore duplicates which can sometimes appear due to high-concurrent process/retry in processQueueAsync
    @NotNull final Collection<HighlightInfo> toolInfos = new HashSetQueue<>(); // guarded by toolInfos
    private int resultCount;
    private boolean favoriteElementStored;

    InspectionProblemHolder(@NotNull PsiFile file,
                            @NotNull LocalInspectionToolWrapper toolWrapper,
                            boolean isOnTheFly,
                            @NotNull InspectionProfileWrapper inspectionProfileWrapper,
                            @NotNull BiConsumer<? super ProblemDescriptor, ? super InspectionProblemHolder> applyIncrementallyCallback) {
      super(InspectionManager.getInstance(file.getProject()), file, isOnTheFly);
      myToolWrapper = toolWrapper;
      myProfileWrapper = inspectionProfileWrapper;
      this.applyIncrementallyCallback = applyIncrementallyCallback;
      synchronized (toolInfos) {
        infoIterator = toolInfos.iterator();
      }
    }

    @Override
    public void registerProblem(@NotNull ProblemDescriptor descriptor) {
      applyIncrementallyCallback.accept(descriptor, this);
      synchronized (this) {
        super.registerProblem(descriptor);
      }
      HighlightSeverity severity = myProfileWrapper.getErrorLevel(myToolWrapper.getDisplayKey(), getFile()).getSeverity();
      if (severity.compareTo(HighlightSeverity.ERROR) >= 0) {
        if (errorStamp == 0) {
          errorStamp = System.nanoTime();
        }
      }
      else if (severity.compareTo(HighlightSeverity.WARNING) >= 0) {
        if (warningStamp == 0) {
          warningStamp = System.nanoTime();
        }
      }
      else {
        if (otherStamp == 0) {
          otherStamp = System.nanoTime();
        }
      }
    }

    @Override
    public synchronized int getResultCount() {
      return super.getResultCount();
    }

    private boolean anyProblemWasReported() {
      return errorStamp > 0 || warningStamp > 0 || otherStamp > 0;
    }

    private void visitElement(@NotNull PsiElement element, @NotNull PsiElementVisitor visitor, @NotNull Document document) {
      element.accept(visitor);
      if (getResultCount() != resultCount) {
        if (!favoriteElementStored) {
          myFavoriteElement = element;
          favoriteElementStored = true;
        }
        resultCount = getResultCount();
        long elementRange = TextRangeScalarUtil.toScalarRange(element.getTextRange());
        synchronized (toolInfos) {
          while (infoIterator.hasNext()) {
            HighlightInfo next = infoIterator.next();
            next.setVisitingTextRange(getFile(), document, elementRange);
          }
        }
      }
    }
  }

  private static boolean shouldInspect(@NotNull PsiFile file) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    HighlightingLevelManager highlightingLevelManager = HighlightingLevelManager.getInstance(file.getProject());
    // for ESSENTIAL mode, it depends on the current phase: when we run regular LocalInspectionPass then don't, when we run Save All handler then run everything
    return highlightingLevelManager != null
           && highlightingLevelManager.shouldInspect(file)
           && !highlightingLevelManager.runEssentialHighlightingOnly(file);
  }
}
