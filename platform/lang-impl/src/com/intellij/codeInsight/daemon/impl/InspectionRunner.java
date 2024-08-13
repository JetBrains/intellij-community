// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.concurrency.JobLauncher;
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.TextRangeScalarUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.util.*;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSetQueue;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

class InspectionRunner {
  private static final Logger LOG = Logger.getInstance(InspectionRunner.class);
  private final PsiFile myPsiFile;
  private final TextRange myRestrictRange;
  private final TextRange myPriorityRange;
  private final boolean myInspectInjected;
  private final boolean myIsOnTheFly;
  private final boolean myDumbMode;
  private final ProgressIndicator myProgress;
  private final boolean myIgnoreSuppressed;
  private final InspectionProfileWrapper myInspectionProfileWrapper;
  private final Map<String, Set<PsiElement>> mySuppressedElements;
  private final List<PsiFile> myInjectedFragments = Collections.synchronizedList(new ArrayList<>());

  InspectionRunner(@NotNull PsiFile psiFile,
                   @NotNull TextRange restrictRange,
                   @NotNull TextRange priorityRange,
                   boolean inspectInjected,
                   boolean isOnTheFly,
                   boolean dumbMode,
                   @NotNull ProgressIndicator progress,
                   boolean ignoreSuppressed,
                   @NotNull InspectionProfileWrapper inspectionProfileWrapper,
                   @NotNull Map<String, Set<PsiElement>> suppressedElements) {
    myPsiFile = psiFile;
    myRestrictRange = restrictRange;
    myPriorityRange = priorityRange;
    myInspectInjected = inspectInjected;
    myIsOnTheFly = isOnTheFly;
    myDumbMode = dumbMode;
    myProgress = progress;
    myIgnoreSuppressed = ignoreSuppressed;
    myInspectionProfileWrapper = inspectionProfileWrapper;
    mySuppressedElements = suppressedElements;
  }

  record InspectionContext(@NotNull LocalInspectionToolWrapper tool,
                           @NotNull InspectionProblemHolder holder,
                           @NotNull PsiElementVisitor visitor,
                           @NotNull List<? extends PsiElement> elementsInside,
                           @NotNull List<? extends PsiElement> elementsOutside,
                           @NotNull List<? extends Class<?>> acceptingPsiTypes,
                           // The containing file this tool was called for. In the case of injected context, this will be the injected file.
                           @NotNull PsiFile psiFile) {
    @Override
    public String toString() {
      return tool +"; inside:"+elementsInside().size()+"; outside:"+elementsOutside().size();
    }
  }

  @NotNull List<? extends InspectionContext> inspect(@NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                                                     @Nullable HighlightSeverity minimumSeverity,
                                                     boolean addRedundantSuppressions,
                                                     @NotNull ApplyIncrementallyCallback applyIncrementallyCallback,
                                                     @NotNull Consumer<? super InspectionContext> contextFinishedCallback,
                                                     @Nullable Condition<? super LocalInspectionToolWrapper> enabledToolsPredicate) {
    if (!shouldInspect(myPsiFile)) {
      return Collections.emptyList();
    }
    List<Divider.DividedElements> allDivided = new ArrayList<>();
    Divider.divideInsideAndOutsideAllRoots(myPsiFile, myRestrictRange, myPriorityRange,
                                           file -> shouldInspect(file), new CommonProcessors.CollectProcessor<>(allDivided));
    List<PsiElement> restrictedInside = ContainerUtil.concat(ContainerUtil.map(allDivided, d -> d.inside()));
    List<PsiElement> restrictedOutside = ContainerUtil.concat(ContainerUtil.map(allDivided, d -> ContainerUtil.concat(d.outside(), d.parents())));
    List<InspectionContext> injectedContexts = Collections.synchronizedList(new ArrayList<>());
    Project project = myPsiFile.getProject();

    boolean hasWholeFileTools = ContainerUtil.exists(toolWrappers, tool->tool.runForWholeFile());
    Set<String> dialectIdsInRestricted = InspectionEngine.calcElementDialectIds(restrictedInside, restrictedOutside);
    Set<String> dialectIdsInWhole;
    List<PsiElement> wholeInside;
    List<PsiElement> wholeOutside;
    if (hasWholeFileTools) {
      List<Divider.DividedElements> all = new ArrayList<>();
      Divider.divideInsideAndOutsideAllRoots(myPsiFile, myPsiFile.getTextRange(), myPriorityRange,
                                             file -> shouldInspect(file), new CommonProcessors.CollectProcessor<>(all));
      wholeInside = ContainerUtil.concat(ContainerUtil.map(all, d -> d.inside()));
      wholeOutside = ContainerUtil.concat(ContainerUtil.map(all, d -> ContainerUtil.concat(d.outside(), d.parents())));
      dialectIdsInWhole = InspectionEngine.calcElementDialectIds(wholeInside, wholeOutside);
    }
    else {
      wholeInside = List.of();
      wholeOutside = List.of();
      dialectIdsInWhole = Set.of();
    }

    List<LocalInspectionToolWrapper> applicableByLanguage =
      InspectionEngine.filterToolsApplicableByLanguage(toolWrappers, dialectIdsInRestricted, dialectIdsInWhole);

    List<InspectionContext> init = new ArrayList<>(applicableByLanguage.size());
    List<InspectionContext> redundantContexts = new ArrayList<>();
    HighlightInfoUpdaterImpl highlightInfoUpdater = (HighlightInfoUpdaterImpl)HighlightInfoUpdater.getInstance(project);
    // might be different from myPriorityRange because DividedElements can cache not exact but containing ranges
    TextRange finalPriorityRange = finalPriorityRange(myPriorityRange, allDivided);
    if (LOG.isTraceEnabled()) {
      LOG.trace("inspect: "+myPsiFile+"; host="+InjectedLanguageManager.getInstance(myPsiFile.getProject()).injectedToHost(myPsiFile, myPsiFile.getTextRange())+";" +
                                    "\n"+" inside:"+restrictedInside.size()+ ": "+restrictedInside+
                                    "\n"+"; outside:"+restrictedOutside.size()+": "+restrictedOutside);
    }
    InspectionEngine.withSession(myPsiFile, myRestrictRange, finalPriorityRange, minimumSeverity, myIsOnTheFly, session -> {
      for (LocalInspectionToolWrapper toolWrapper : applicableByLanguage) {
        if (enabledToolsPredicate == null || enabledToolsPredicate.value(toolWrapper)) {
          LocalInspectionTool tool = toolWrapper.getTool();
          AtomicInteger toolWasProcessed = new AtomicInteger();
          ToolStampInfo toolStamps = new ToolStampInfo();
          InspectionProblemHolder holder = new InspectionProblemHolder(myPsiFile, toolWrapper, myIsOnTheFly, myInspectionProfileWrapper,
                                                                       toolWasProcessed, toolStamps, applyIncrementallyCallback);
          PsiElementVisitor visitor = InspectionEngine.createVisitor(tool, holder, myIsOnTheFly, session);
          // if inspection returned the empty visitor, then it should be skipped
          if (visitor == PsiElementVisitor.EMPTY_VISITOR) {
            continue;
          }
          tool.inspectionStarted(session, myIsOnTheFly);

          List<? extends PsiElement> sortedInside = HighlightInfoUpdaterImpl.sortByPsiElementFertility(myPsiFile, toolWrapper, toolWrapper.runForWholeFile() ? wholeInside : restrictedInside);
          List<? extends PsiElement> outside = toolWrapper.runForWholeFile() ? wholeOutside : restrictedOutside;
          InspectionContext context = new InspectionContext(toolWrapper, holder, visitor, sortedInside, outside, InspectionVisitorOptimizer.getAcceptingPsiTypes(visitor), myPsiFile);
          init.add(context);
        }
      }
      //sort `init`, according to the priorities, saved earlier to run in order
      // but only for visible elements, because we don't care about the order in 'outside', and spending CPU on their rearrangement would be counterproductive
      InspectionProfilerDataHolder.sortByLatencies(myPsiFile, init, highlightInfoUpdater);

      Processor<? super InspectionContext> contextProcessor = (context) -> {
        executeInImpatientReadAction(()-> {
          if (DumbService.isDumb(project) != myDumbMode) {
            // Dumb state change has sneaked between our read actions. Aborting.
            return;
          }
          // sequentially to avoid inspection visitor reentrancy
          processContext(context, context.elementsInside(), new InspectionVisitorOptimizer(context.elementsInside()));
          processContext(context, context.elementsOutside(), new InspectionVisitorOptimizer(context.elementsOutside()));
          contextCompleted(context, session, contextFinishedCallback);
        });
        return true;
      };
      // start InspectionContexts in `init` parallel to discovering injected fragments and running inspection runner on them too.
      // note that the parallelism is restricted: all InspectionContexts from `init` are run in parallel, within each
      // the InspectionContext with (visible=true) is run and then InspectionContext with (visible=false).
      // Thus, we avoid running the same inspection tool visitor in a reentrant manner (on visible elements parallel to invisible elements),
      // because some of them are not ready for that.
      if (!JobLauncher.getInstance().processConcurrentlyAsync(new SensitiveProgressWrapper(myProgress), init, contextProcessor, () -> {
        // have to do all this even for empty elements, to perform correct cleanup/inspectionFinished
        reportIdsOfInspectionsReportedAnyProblemToFUS(init);

        if (myInspectInjected && InjectionUtils.shouldInspectInjectedFiles(myPsiFile)) {
          // we don't run whole-file tools on injected fragments
          List<LocalInspectionToolWrapper> localTools = ContainerUtil.filter(toolWrappers, t -> !t.runForWholeFile());
          getInjectedWithHosts(
            ContainerUtil.concat(restrictedInside, restrictedOutside), session,
            localTools, injectedContexts, applyIncrementallyCallback,
            contextFinishedCallback, enabledToolsPredicate);
        }
      })) {
        throw new ProcessCanceledException();
      }

      boolean isWholeFileInspectionsPass = !init.isEmpty() && init.get(0).tool.runForWholeFile();
      if (myIsOnTheFly && !isWholeFileInspectionsPass) {
        // do not save stats for the batch process, there could be too many files
        InspectionProfilerDataHolder.saveStats(myPsiFile, init, highlightInfoUpdater);
      }
      if (myIsOnTheFly && addRedundantSuppressions) {
        addRedundantSuppressions(init, toolWrappers, redundantContexts, applyIncrementallyCallback, contextFinishedCallback);
      }
    });
    return ContainerUtil.concat(init, redundantContexts, injectedContexts);
  }

  private static void contextCompleted(@NotNull InspectionContext context,
                                       @NotNull LocalInspectionToolSession session,
                                       @NotNull Consumer<? super InspectionContext> contextFinishedCallback) {
    if (LOG.isTraceEnabled()) {
      LOG.trace("onComplete: " + context + "; holder:"+context.holder.getResults());
    }
    // both 'inside' and 'outside' elements are visited, the inspection is considered finished
    InspectionProblemHolder holder = context.holder;
    holder.toolStamps.finishTimeStamp = System.nanoTime();
    context.tool.getTool().inspectionFinished(session, holder);
    // report descriptors which were added by crazy inspections in their inspectionFinished() as the result of "visiting" fake element
    holder.reportAddedDescriptors(HighlightInfoUpdaterImpl.FAKE_ELEMENT);
    contextFinishedCallback.accept(context);
  }

  private void reportIdsOfInspectionsReportedAnyProblemToFUS(@NotNull List<? extends InspectionContext> init) {
    if (!StatisticsUploadAssistant.isCollectAllowedOrForced()) return;
    Set<String> inspectionIdsReportedProblems = new HashSet<>();
    InspectionProblemHolder holder;
    for (InspectionContext context : init) {
      holder = context.holder;
      if (holder.toolStamps.anyProblemWasReported()) {
        inspectionIdsReportedProblems.add(context.tool.getID());
      }
    }
    InspectionUsageFUSStorage.getInstance(myPsiFile.getProject()).reportInspectionsWhichReportedProblems(inspectionIdsReportedProblems);
  }

  private static @NotNull TextRange finalPriorityRange(@NotNull TextRange priorityRange, @NotNull List<? extends Divider.DividedElements> allDivided) {
    long finalPriorityRange = allDivided.isEmpty() ? TextRangeScalarUtil.toScalarRange(priorityRange) : allDivided.get(0).priorityRange();
    for (int i = 1; i < allDivided.size(); i++) {
      Divider.DividedElements dividedElements = allDivided.get(i);
      finalPriorityRange = TextRangeScalarUtil.union(finalPriorityRange, dividedElements.priorityRange());
    }
    return TextRangeScalarUtil.create(finalPriorityRange);
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
                                        @NotNull ApplyIncrementallyCallback applyIncrementallyCallback,
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
        continue;
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
    RedundantSuppressInspectionBase redundantSuppressGlobalTool = (RedundantSuppressInspectionBase)redundantSuppressTool.getTool();
    LocalInspectionTool rsLocalTool = redundantSuppressGlobalTool.createLocalTool(redundantSuppressionDetector, mySuppressedElements, activeTools, myRestrictRange);
    List<LocalInspectionToolWrapper> wrappers = Collections.singletonList(new LocalInspectionToolWrapper(rsLocalTool));
    InspectionRunner runner = new InspectionRunner(myPsiFile, myRestrictRange, myPriorityRange, myInspectInjected, true,
                                                   myDumbMode, myProgress, false, myInspectionProfileWrapper,
                                                   mySuppressedElements);
    result.addAll(runner.inspect(wrappers, HighlightSeverity.WARNING, false, applyIncrementallyCallback, contextFinishedCallback, null));
  }

  private void executeInImpatientReadAction(@NotNull Runnable runnable) {
    ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    boolean shouldFailFastAcquiringReadAction = application.isInImpatientReader();

    AstLoadingFilter.disallowTreeLoading(() -> AstLoadingFilter.<Boolean, RuntimeException>forceAllowTreeLoading(myPsiFile, () -> {
      if (shouldFailFastAcquiringReadAction) {
        application.executeByImpatientReader(() -> {
          if (!application.tryRunReadAction(runnable)) {
            throw new ProcessCanceledException();
          }
        });
      }
      else {
        if (!application.tryRunReadAction(runnable)) {
          throw new ProcessCanceledException();
        }
      }
      return true;
    }));
  }

  /**
   * for each tool in {@code init} (in this order) call its {@link InspectionContext#visitor()} on every PsiElement in {@code inside} list
   * (starting from this inspection's most fertile elements if any),
   * maintaining parallelism during this process (i.e., several visitors from {@code init} can be executed concurrently, but elements from the list head get higher priority than the list tail).
   */
  private static void processContext(@NotNull InspectionContext context, List<? extends PsiElement> psiElements,
                                     @NotNull InspectionVisitorOptimizer optimizer) {
    if (LOG.isTraceEnabled()) {
      LOG.trace("processContext: " +
                context + "; elements(" + psiElements.size() + "): " + StringUtil.join(psiElements, e-> e + "(" + e.getClass() + ")", ", ") + "; accepts=" + context.acceptingPsiTypes());
    }
    optimizer.acceptElements(psiElements,
                             context.acceptingPsiTypes,
                             psiElement -> context.holder.visitElement(psiElement, context.visitor));
  }

  private void injectedFound(@NotNull PsiFile injectedPsi, @NotNull PsiElement host, @NotNull LocalInspectionToolSession session,
                             @NotNull List<? extends LocalInspectionToolWrapper> wrappers,
                             @NotNull List<? super InspectionContext> outInjectedContexts,
                             @NotNull ApplyIncrementallyCallback addDescriptorIncrementallyCallback,
                             @NotNull Consumer<? super InspectionContext> contextFinishedCallback,
                             @Nullable Condition<? super LocalInspectionToolWrapper> enabledToolsPredicate) {
    if (!shouldInspect(injectedPsi)) {
      return;
    }
    // convert host priority range to the injected
    Document document = PsiDocumentManager.getInstance(myPsiFile.getProject()).getDocument(injectedPsi);
    TextRange injectedPriorityRange;
    if (document instanceof DocumentWindow documentWindow) {
      int start = documentWindow.hostToInjected(myPriorityRange.getStartOffset());
      int end = documentWindow.hostToInjected(myPriorityRange.getEndOffset());
      injectedPriorityRange = TextRange.isProperRange(start, end) ? new TextRange(start, end) : TextRange.EMPTY_RANGE;

      if (LOG.isTraceEnabled()) {
        LOG.trace("startInspectingInjectedPsi: psi=" + injectedPsi + "; host ranges= " + Arrays.toString(documentWindow.getHostRanges()) + "; wrappers=" +
                  wrappers + "; injectedPsi.getTextRange()=" + injectedPsi.getTextRange() + "; shouldInspect=" + shouldInspect(injectedPsi));
      }
    }
    else {
      injectedPriorityRange = TextRange.EMPTY_RANGE;
      if (LOG.isTraceEnabled()) {
        LOG.trace("startInspectingInjectedPsi: psi=" + injectedPsi + "; document = " + document + "; wrappers=" +
                  wrappers + "; injectedPsi.getTextRange()=" + injectedPsi.getTextRange() + "; shouldInspect=" + shouldInspect(injectedPsi));
      }
    }
    InspectionRunner injectedRunner = new InspectionRunner(injectedPsi, injectedPsi.getTextRange(), injectedPriorityRange,
                                                           false, myIsOnTheFly, myDumbMode, myProgress, myIgnoreSuppressed,
                                                           myInspectionProfileWrapper, mySuppressedElements);
    ApplyIncrementallyCallback applyInjectionsIncrementallyCallback = (descriptors, holder, visitingPsiElement, shortName) ->
      applyInjectedDescriptor(descriptors, holder, visitingPsiElement, shortName, host, addDescriptorIncrementallyCallback);
    List<? extends InspectionContext> injectedContexts = injectedRunner.inspect(
      wrappers, session.getMinimumSeverity(), true, applyInjectionsIncrementallyCallback,
      contextFinishedCallback, enabledToolsPredicate);
    outInjectedContexts.addAll(injectedContexts);
  }

  private void applyInjectedDescriptor(@NotNull List<? extends ProblemDescriptor> descriptors,
                                       @NotNull InspectionProblemHolder holder,
                                       @NotNull PsiElement visitingPsiElement,
                                       @NotNull String shortName,
                                       @NotNull PsiElement host,
                                       @NotNull ApplyIncrementallyCallback addDescriptorIncrementallyCallback) {
    if (LOG.isTraceEnabled()) {
      LOG.trace("startInspectingInjectedPsi:applyInjectedDescriptor visitingPsiElement=" + visitingPsiElement + "; tool=" + shortName + "; desc=" +
                                    descriptors + "; holder=" + holder.getResults() + "; onTheFly=" + myIsOnTheFly);
    }
    Boolean isSuppressedForHost = null;
    if (myIgnoreSuppressed) {
      for (int i = descriptors.size() - 1; i >= 0; i--) {
        ProblemDescriptor descriptor = descriptors.get(i);
        PsiElement descriptorPsiElement = descriptor.getPsiElement();
        LocalInspectionToolWrapper wrapper = holder.myToolWrapper;
        if (isSuppressedForHost == null) {
          isSuppressedForHost = wrapper.getTool().isSuppressedFor(host);
        }
        if (isSuppressedForHost || descriptorPsiElement != null && wrapper.getTool().isSuppressedFor(descriptorPsiElement)) {
          registerSuppressedElements(host, wrapper.getID(), wrapper.getAlternativeID());
          // remove descriptor at index i from applying
          descriptors = ContainerUtil.concat(descriptors.subList(0, i), descriptors.subList(i+1, descriptors.size()));
          if (LOG.isTraceEnabled()) {
            LOG.trace("startInspectingInjectedPsi:applyInjectedDescriptor: suppressed " + descriptor + " for tool " + wrapper);
          }
        }
      }
    }
    if (myIsOnTheFly) {
      addDescriptorIncrementallyCallback.apply(descriptors, holder, visitingPsiElement, shortName);
    }
  }

  private void getInjectedWithHosts(@NotNull List<? extends PsiElement> elements,
                                    @NotNull LocalInspectionToolSession session,
                                    @NotNull List<? extends LocalInspectionToolWrapper> wrappers,
                                    @NotNull List<? super InspectionContext> outInjectedContexts,
                                    @NotNull ApplyIncrementallyCallback addDescriptorIncrementallyCallback,
                                    @NotNull Consumer<? super InspectionContext> contextFinishedCallback,
                                    @Nullable Condition<? super LocalInspectionToolWrapper> enabledToolsPredicate) {
    Map<PsiFile, PsiElement> injectedToHost = Collections.synchronizedMap(createInjectedFileMap());
    Project project = myPsiFile.getProject();
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(elements, myProgress, element -> {
      InjectedLanguageManager.getInstance(project).enumerateEx(element, myPsiFile, false, (injectedPsi, places) -> {
         if (injectedToHost.put(injectedPsi, element) == null) {
           if (LOG.isTraceEnabled()) {
             LOG.trace("getInjectedWithHosts: found injected " +injectedPsi+ " at "+places.size()+" places: "+places+"; "+injectedPsi);
           }
           injectedFound(injectedPsi, element, session, wrappers, outInjectedContexts, addDescriptorIncrementallyCallback, contextFinishedCallback, enabledToolsPredicate);
         }
      });
      return true;
    });
    myInjectedFragments.addAll(injectedToHost.keySet());
  }

  interface ApplyIncrementallyCallback {
    void apply(@NotNull List<? extends ProblemDescriptor> descriptors,
               @NotNull InspectionProblemHolder holder,
               @NotNull PsiElement visitingPsiElement,
               @NotNull String toolShortName);
  }
  static class ToolStampInfo {
    long errorStamp; // nano-stamp of the first error created
    long warningStamp; // nano-stamp of the first warning created
    long otherStamp; // nano-stamp of the first created problem descriptor with severity other than error/warn
    final long initTimeStamp = System.nanoTime();
    volatile long finishTimeStamp;
    private void updateToolStamps(@NotNull HighlightSeverity severity) {
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
    private boolean anyProblemWasReported() {
      return errorStamp > 0 || warningStamp > 0 || otherStamp > 0;
    }
  }

  static final class InspectionProblemHolder extends ProblemsHolder {
    final @NotNull LocalInspectionToolWrapper myToolWrapper;
    private final InspectionProfileWrapper myProfileWrapper;
    private final @NotNull ApplyIncrementallyCallback applyIncrementallyCallback;
    final @NotNull AtomicInteger toolWasProcessed;
    // has to ignore duplicates which can sometimes appear due to high-concurrent process/retry in processQueueAsync
    final @NotNull Collection<HighlightInfo> toolInfos = new HashSetQueue<>(); // guarded by toolInfos
    private int resultCount;
    final ToolStampInfo toolStamps;

    InspectionProblemHolder(@NotNull PsiFile file,
                            @NotNull LocalInspectionToolWrapper toolWrapper,
                            boolean isOnTheFly,
                            @NotNull InspectionProfileWrapper inspectionProfileWrapper,
                            @NotNull AtomicInteger toolWasProcessed,
                            @NotNull ToolStampInfo toolStamps,
                            @NotNull ApplyIncrementallyCallback applyIncrementallyCallback) {
      super(InspectionManager.getInstance(file.getProject()), file, isOnTheFly);
      myToolWrapper = toolWrapper;
      myProfileWrapper = inspectionProfileWrapper;
      this.applyIncrementallyCallback = applyIncrementallyCallback;
      this.toolWasProcessed = toolWasProcessed;
      this.toolStamps = toolStamps;
    }

    @Override
    protected void saveProblem(@NotNull ProblemDescriptor descriptor) {
      synchronized (this) {
        super.saveProblem(descriptor);
      }
      toolStamps.updateToolStamps(myProfileWrapper.getErrorLevel(myToolWrapper.getDisplayKey(), getFile()).getSeverity());
    }

    @Override
    public synchronized int getResultCount() {
      return super.getResultCount();
    }

    private void visitElement(@NotNull PsiElement element, @NotNull PsiElementVisitor visitor) {
      element.accept(visitor);
      reportAddedDescriptors(element);
    }

    private void reportAddedDescriptors(@NotNull PsiElement element) {
      int newCount = getResultCount();
      int oldCount = resultCount;
      List<ProblemDescriptor> descriptors;
      if (newCount == oldCount) {
        descriptors = Collections.emptyList();
      }
      else {
        List<ProblemDescriptor> results = getResults();
        descriptors = results.subList(oldCount, newCount);
        resultCount = newCount;
      }
      applyIncrementallyCallback.apply(descriptors, this, element, myToolWrapper.getShortName());
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

  @NotNull
  List<PsiFile> getInjectedFragments() {
    return myInjectedFragments;
  }
}
