// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.concurrency.JobLauncher;
import com.intellij.concurrency.JobLauncherImpl;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.AstLoadingFilter;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
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

  static class InspectionContext {
    private InspectionContext(@NotNull LocalInspectionToolWrapper tool,
                              @NotNull InspectionProblemHolder holder,
                              @NotNull PsiElementVisitor visitor) {
      this.tool = tool;
      this.holder = holder;
      this.visitor = visitor;
    }

    final @NotNull LocalInspectionToolWrapper tool;
    final @NotNull InspectionProblemHolder holder;
    final @NotNull PsiElementVisitor visitor;
    volatile PsiElement myFavoriteElement; // the element during visiting which some diagnostics were generated in previous run
  }

  @NotNull List<? extends InspectionContext> inspect(@NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                                                     boolean addRedundantSuppressions,
                                                     @NotNull BiPredicate<? super ProblemDescriptor, ? super LocalInspectionToolWrapper> applyIncrementallyCallback,
                                                     @NotNull Consumer<? super InspectionContext> afterInsideProcessedCallback,
                                                     @NotNull Consumer<? super InspectionContext> afterOutsideProcessedCallback) {
    if (!shouldRun()) {
      return Collections.emptyList();
    }
    List<Divider.DividedElements> allDivided = new ArrayList<>();
    Divider.divideInsideAndOutsideAllRoots(myPsiFile, myRestrictRange, myPriorityRange,
                                           file -> HighlightingLevelManager.getInstance(file.getProject()).shouldInspect(file), new CommonProcessors.CollectProcessor<>(allDivided));
    List<PsiElement> inside = ContainerUtil.concat((List<List<PsiElement>>)ContainerUtil.map(allDivided, d -> d.inside));
    // might be different from myPriorityRange because DividedElements can cache not exact but containing ranges
    long finalPriorityRange = finalPriorityRange(myPriorityRange, allDivided);
    List<PsiElement> outside = ContainerUtil.concat((List<List<PsiElement>>)ContainerUtil.map(allDivided, d -> ContainerUtil.concat(d.outside, d.parents)));
    List<InspectionContext> injectedContexts = Collections.synchronizedList(new ArrayList<>());

    List<LocalInspectionToolWrapper> filteredWrappers =
      InspectionEngine.filterToolsApplicableByLanguage(toolWrappers, InspectionEngine.calcElementDialectIds(inside, outside));
    List<InspectionContext> init = new ArrayList<>(filteredWrappers.size());
    List<InspectionContext> redundantContexts = new ArrayList<>();
    InspectionEngine.withSession(myPsiFile, myRestrictRange, TextRange.create(finalPriorityRange), myIsOnTheFly, session -> {
      long start = System.nanoTime();
      filteredWrappers.forEach(wrapper -> ContainerUtil.addIfNotNull(init, createContext(wrapper, session,
                                                                                         applyIncrementallyCallback)));
      //sort init according to the priorities saved earlier to run in order
      InspectionProfilerDataHolder profileData = InspectionProfilerDataHolder.getInstance(myPsiFile.getProject());
      profileData.sort(myPsiFile, init);
      profileData.retrieveFavoriteElements(myPsiFile, init);

      // injected -> host
      Map<PsiFile, PsiElement> foundInjected = createInjectedFileMap();
      InspectionContext TOMB_STONE = createTombStone();
      visitElements(init, inside, true, finalPriorityRange, TOMB_STONE, afterInsideProcessedCallback, foundInjected, injectedContexts,
                    toolWrappers, applyIncrementallyCallback);

      Consumer<InspectionContext> afterOutside = context -> {
              InspectionProblemHolder holder = context.holder;
              holder.finishTimeStamp = System.nanoTime();
              context.tool.getTool().inspectionFinished(session, holder);
              afterOutsideProcessedCallback.accept(context);
      };
      visitElements(init, outside, false, finalPriorityRange, TOMB_STONE, afterOutside, foundInjected, injectedContexts,
                    toolWrappers, empty());
      reportStatsToQodana(init);
      boolean isWholeFileInspectionsPass = !toolWrappers.isEmpty() && toolWrappers.get(0).getTool().runForWholeFile();
      if (myIsOnTheFly && !isWholeFileInspectionsPass) {
        // do not save stats for batch process, there could be too many files
        InspectionProfilerDataHolder.getInstance(myPsiFile.getProject()).saveStats(myPsiFile, init, System.nanoTime() - start);
      }
      if (myIsOnTheFly && addRedundantSuppressions) {
        addRedundantSuppressions(init, toolWrappers, redundantContexts);
      }
    });

    return ContainerUtil.concat(init, redundantContexts, injectedContexts);
  }

  private static long finalPriorityRange(@NotNull TextRange priorityRange, @NotNull List<? extends Divider.DividedElements> allDivided) {
    long finalPriorityRange = allDivided.isEmpty() ? priorityRange.toScalarRange() : allDivided.get(0).priorityRange;
    for (int i = 1; i < allDivided.size(); i++) {
      Divider.DividedElements dividedElements = allDivided.get(i);
      finalPriorityRange = TextRange.union(finalPriorityRange, dividedElements.priorityRange);
    }
    return finalPriorityRange;
  }

  @NotNull
  private InspectionContext createTombStone() {
    LocalInspectionToolWrapper tool = new LocalInspectionToolWrapper(new LocalInspectionEP());
    InspectionProblemHolder holder = new InspectionProblemHolder(myPsiFile, tool, false, myInspectionProfileWrapper, empty());
    return new InspectionContext(tool, holder, new PsiElementVisitor() {});
  }

  @NotNull
  private static <T> Map<PsiFile, T> createInjectedFileMap() {
    // TODO remove when injected PsiFile implemented equals() base on its offsets in the host
    return CollectionFactory.createCustomHashingStrategyMap(new HashingStrategy<>() {
      @Override
      public int hashCode(PsiFile f) {
        if (f == null) return 0;
        VirtualFile v = f.getVirtualFile();
        VirtualFile host = v instanceof VirtualFileWindow ? ((VirtualFileWindow)v).getDelegate() : null;
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
        if (!(v1 instanceof VirtualFileWindow) || !(v2 instanceof VirtualFileWindow)) {
          return Objects.equals(v1, v2);
        }
        VirtualFile d1 = ((VirtualFileWindow)v1).getDelegate();
        VirtualFile d2 = ((VirtualFileWindow)v2).getDelegate();
        if (!Objects.equals(d1, d2)) {
          return false;
        }
        return Arrays.equals(((VirtualFileWindow)v1).getDocumentWindow().getHostRanges(), ((VirtualFileWindow)v2).getDocumentWindow().getHostRanges());
      }
    });
  }

  private void reportStatsToQodana(@NotNull List<? extends InspectionContext> contexts) {
    if (!myIsOnTheFly) {
      Project project = myPsiFile.getProject();
      InspectListener publisher = project.getMessageBus().syncPublisher(GlobalInspectionContextEx.INSPECT_TOPIC);
      for (InspectionContext context : contexts) {
        LocalInspectionToolWrapper toolWrapper = context.tool;
        InspectionProblemHolder holder = context.holder;
        long durationMs = TimeUnit.NANOSECONDS.toMillis(holder.finishTimeStamp - holder.initTimeStamp);
        int problemCount = context.holder.getResultCount();
        publisher.inspectionFinished(durationMs, 0, problemCount, toolWrapper, InspectListener.InspectionKind.LOCAL, myPsiFile, project);
      }
    }
  }

  private void registerSuppressedElements(@NotNull PsiElement element, @NotNull String id, @Nullable String alternativeID) {
    mySuppressedElements.computeIfAbsent(id, __ -> new HashSet<>()).add(element);
    if (alternativeID != null) {
      mySuppressedElements.computeIfAbsent(alternativeID, __ -> new HashSet<>()).add(element);
    }
  }

  private void addRedundantSuppressions(@NotNull List<? extends InspectionContext> init,
                                        @NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                                        @NotNull List<? super InspectionContext> result) {
    for (InspectionContext context : init) {
      LocalInspectionToolWrapper toolWrapper = context.tool;
      LocalInspectionTool tool = toolWrapper.getTool();
      for (ProblemDescriptor descriptor : context.holder.getResults()) {
        PsiElement element = descriptor.getPsiElement();
        if (element != null) {
          if (tool.isSuppressedFor(element)) {
            registerSuppressedElements(element, toolWrapper.getID(), toolWrapper.getAlternativeID());
          }
        }
      }
    }
    HighlightDisplayKey redundantSuppressionKey = HighlightDisplayKey.find(RedundantSuppressInspectionBase.SHORT_NAME);
    InspectionProfile inspectionProfile = myInspectionProfileWrapper.getInspectionProfile();
    if (redundantSuppressionKey == null || !inspectionProfile.isToolEnabled(redundantSuppressionKey, myPsiFile)) {
      return;
    }
    InspectionToolWrapper<?,?> toolWrapper = inspectionProfile.getInspectionTool(RedundantSuppressInspectionBase.SHORT_NAME, myPsiFile);
    Language fileLanguage = myPsiFile.getLanguage();
    InspectionSuppressor suppressor = LanguageInspectionSuppressors.INSTANCE.forLanguage(fileLanguage);
    if (!(suppressor instanceof RedundantSuppressionDetector)) {
      return;
    }
    RedundantSuppressionDetector redundantSuppressionDetector = (RedundantSuppressionDetector)suppressor;
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
    LocalInspectionTool localTool = ((RedundantSuppressInspectionBase)toolWrapper.getTool()).createLocalTool(redundantSuppressionDetector, mySuppressedElements, activeTools);
    List<LocalInspectionToolWrapper> wrappers = Collections.singletonList(new LocalInspectionToolWrapper(localTool));
    InspectionRunner runner = new InspectionRunner(myPsiFile, myRestrictRange, myPriorityRange, myInspectInjected, true, myProgress, false,
                                                   myInspectionProfileWrapper, mySuppressedElements);
    result.addAll(runner.inspect(wrappers, false, empty(), emptyCallback(), emptyCallback()));
  }

  @NotNull
  private static Consumer<InspectionContext> emptyCallback() { return __ -> { }; }

  @NotNull
  private static BiPredicate<ProblemDescriptor, LocalInspectionToolWrapper> empty() { return (_1, _2) -> true; }

  private void visitElements(@NotNull List<? extends InspectionContext> init,
                             @NotNull List<? extends PsiElement> elements,
                             boolean inVisibleRange,
                             long finalPriorityRange,
                             @NotNull InspectionContext TOMB_STONE,
                             @NotNull Consumer<? super InspectionContext> afterProcessCallback,
                             @NotNull Map<? super PsiFile, PsiElement> foundInjected,
                             @NotNull List<? super InspectionContext> injectedInsideContexts,
                             @NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                             @NotNull BiPredicate<? super ProblemDescriptor, ? super LocalInspectionToolWrapper> applyIncrementallyCallback) {
    processInOrder(init, elements, inVisibleRange, finalPriorityRange, TOMB_STONE, afterProcessCallback);

    if (myInspectInjected) {
      inspectInjectedPsi(elements, toolWrappers, foundInjected, injectedInsideContexts, applyIncrementallyCallback);
    }
  }

  /**
   * for each tool in {@code init} (in this order) call its {@link InspectionContext#visitor} on every PsiElement in {@code inside} list (starting from this inspection's {@link InspectionContext#myFavoriteElement} if any),
   * maintaining parallelism during this process (i.e., several visitors from {@code init} can be executed concurrently, but elements from the list head get higher priority than the list tail).
   */
  private void processInOrder(@NotNull List<? extends InspectionContext> init,
                              @NotNull List<? extends PsiElement> elements,
                              boolean isInsideVisibleRange,
                              long priorityRange,
                              @NotNull InspectionContext TOMB_STONE,
                              @NotNull Consumer<? super InspectionContext> afterProcessCallback) {
    // have to do all this even for empty elements, in order to perform correct cleanup/inspectionFinished
    BlockingQueue<InspectionContext> contexts = new ArrayBlockingQueue<>(init.size() + 1, false, init);
    boolean added = contexts.offer(TOMB_STONE);
    assert added;
    ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    boolean shouldFailFastAcquiringReadAction = application.isInImpatientReader();

    boolean processed =
      ((JobLauncherImpl)JobLauncher.getInstance()).processQueue(contexts, new LinkedBlockingQueue<>(), new SensitiveProgressWrapper(myProgress), TOMB_STONE, context ->
        AstLoadingFilter.disallowTreeLoading(() -> AstLoadingFilter.<Boolean, RuntimeException>forceAllowTreeLoading(myPsiFile, () -> {
          Runnable runnable = () -> {
            if (!application.tryRunReadAction(() -> {
              InspectionProblemHolder holder = context.holder;
              int resultCount = holder.getResultCount();
              PsiElement favoriteElement = context.myFavoriteElement;

              // accept favoriteElement only if it belongs to the correct inside/outside list
              if (favoriteElement != null && isInsideVisibleRange == favoriteElement.getTextRange().intersects(priorityRange)) {
                context.myFavoriteElement = null; // null the element to make sure it will hold the new favorite after this method finished
                // run first for the element we know resulted in the diagnostics during previous run
                favoriteElement.accept(context.visitor);
                if (holder.getResultCount() != resultCount && resultCount != -1) {
                  context.myFavoriteElement = favoriteElement;
                  resultCount = -1; // mark as "new favorite element is stored"
                }
              }

              //noinspection ForLoopReplaceableByForEach
              for (int i = 0; i < elements.size(); i++) {
                PsiElement element = elements.get(i);
                ProgressManager.checkCanceled();
                if (element == favoriteElement) continue; // already visited
                element.accept(context.visitor);
                if (resultCount != -1 && holder.getResultCount() != resultCount) {
                  context.myFavoriteElement = element;
                  resultCount = -1; // mark as "new favorite element is stored"
                }
              }
              afterProcessCallback.accept(context);
            })) {
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
    if (!processed) {
      throw new ProcessCanceledException();
    }
  }

  private InspectionContext createContext(@NotNull LocalInspectionToolWrapper toolWrapper,
                                          @NotNull LocalInspectionToolSession session,
                                          @NotNull BiPredicate<? super ProblemDescriptor, ? super LocalInspectionToolWrapper> applyIncrementallyCallback) {
    LocalInspectionTool tool = toolWrapper.getTool();
    InspectionProblemHolder holder = new InspectionProblemHolder(myPsiFile, toolWrapper, myIsOnTheFly,
                                                                 myInspectionProfileWrapper, applyIncrementallyCallback);

    PsiElementVisitor visitor = InspectionEngine.createVisitor(tool, holder, myIsOnTheFly, session);
    // if inspection returned empty visitor then it should be skipped
    if (visitor != PsiElementVisitor.EMPTY_VISITOR) {
      tool.inspectionStarted(session, myIsOnTheFly);
      return new InspectionContext(toolWrapper, holder, visitor);
    }
    return null;
  }

  private void inspectInjectedPsi(@NotNull List<? extends PsiElement> elements,
                                  @NotNull List<? extends LocalInspectionToolWrapper> wrappers,
                                  @NotNull Map<? super PsiFile, PsiElement> injectedToHost,
                                  @NotNull List<? super InspectionContext> outInjectedContexts,
                                  @NotNull BiPredicate<? super ProblemDescriptor, ? super LocalInspectionToolWrapper> addDescriptorIncrementallyCallback) {
    Project project = myPsiFile.getProject();
    List<PsiFile> toInspect = new ArrayList<>();
    for (PsiElement element : elements) {
      InjectedLanguageManager.getInstance(project).enumerateEx(element, myPsiFile, false,
                                                               (injectedPsi, __) -> {
                                                                 if (injectedToHost.put(injectedPsi, element) == null) {
                                                                   toInspect.add(injectedPsi);
                                                                 }
                                                               });
    }
    if (toInspect.isEmpty()) {
      return;
    }
    if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(new ArrayList<>(toInspect), myProgress,
         injectedPsi -> {
           PsiElement host = injectedToHost.get(injectedPsi);

           BiPredicate<? super ProblemDescriptor, ? super LocalInspectionToolWrapper> cc = (descriptor, wrapper) -> {
             PsiElement descriptorPsiElement = descriptor.getPsiElement();
             if (myIgnoreSuppressed &&
                 (host != null && wrapper.getTool().isSuppressedFor(host)
                  || descriptorPsiElement != null && wrapper.getTool().isSuppressedFor(descriptorPsiElement))) {
               registerSuppressedElements(host, wrapper.getID(), wrapper.getAlternativeID());
               return false;
             }
             if (myIsOnTheFly) {
               return addDescriptorIncrementallyCallback.test(descriptor, wrapper);
             }
             return true;
           };

           if (HighlightingLevelManager.getInstance(injectedPsi.getProject()).shouldInspect(injectedPsi)) {
             InspectionRunner injectedRunner =
               new InspectionRunner(injectedPsi, injectedPsi.getTextRange(), injectedPsi.getTextRange(), false, myIsOnTheFly, myProgress,
                                    myIgnoreSuppressed, myInspectionProfileWrapper, mySuppressedElements);
             outInjectedContexts.addAll(injectedRunner.inspect(wrappers, true, cc, emptyCallback(), emptyCallback()));
           }
           return true;
         })) {
      throw new ProcessCanceledException();
    }
  }

  static class InspectionProblemHolder extends ProblemsHolder {
    private @NotNull final LocalInspectionToolWrapper myToolWrapper;
    private final InspectionProfileWrapper myProfileWrapper;
    private final BiPredicate<? super ProblemDescriptor, ? super LocalInspectionToolWrapper> applyIncrementallyCallback;
    volatile boolean applyIncrementally;
    long errorStamp; // nano-stamp of the first error created
    long warningStamp; // nano-stamp of the first warning created
    long otherStamp; // nano-stamp of the first info/weak warn/etc. created
    final long initTimeStamp = System.nanoTime();
    volatile long finishTimeStamp;

    InspectionProblemHolder(@NotNull PsiFile file,
                            @NotNull LocalInspectionToolWrapper toolWrapper,
                            boolean isOnTheFly,
                            @NotNull InspectionProfileWrapper inspectionProfileWrapper,
                            @NotNull BiPredicate<? super ProblemDescriptor, ? super LocalInspectionToolWrapper> applyIncrementallyCallback) {
      super(InspectionManager.getInstance(file.getProject()), file, isOnTheFly);
      myToolWrapper = toolWrapper;
      myProfileWrapper = inspectionProfileWrapper;
      this.applyIncrementallyCallback = applyIncrementallyCallback;
      applyIncrementally = isOnTheFly;
    }

    @Override
    public void registerProblem(@NotNull ProblemDescriptor descriptor) {
      if (applyIncrementally && !applyIncrementallyCallback.test(descriptor, myToolWrapper)) {
        return;
      }
      super.registerProblem(descriptor);
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
  }

  private boolean shouldRun() {
    HighlightingLevelManager highlightingLevelManager = HighlightingLevelManager.getInstance(myPsiFile.getProject());
    // for ESSENTIAL mode, it depends on the current phase: when we run regular LocalInspectionPass then don't, when we run Save All handler then run everything
    return highlightingLevelManager.shouldInspect(myPsiFile) && !highlightingLevelManager.runEssentialHighlightingOnly(myPsiFile);
  }
}
