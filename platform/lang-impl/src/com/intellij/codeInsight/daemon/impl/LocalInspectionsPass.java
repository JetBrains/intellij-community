// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.DaemonTooltipsUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ProblemDescriptorUtil.ProblemPresentation;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.concurrency.JobLauncher;
import com.intellij.concurrency.JobLauncherImpl;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.diagnostic.PluginException;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.markup.UnmodifiableTextAttributes;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.util.*;
import com.intellij.util.containers.*;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class LocalInspectionsPass extends ProgressableTextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance(LocalInspectionsPass.class);
  public static final TextRange EMPTY_PRIORITY_RANGE = TextRange.EMPTY_RANGE;
  private static final Predicate<PsiFile> SHOULD_INSPECT_FILTER = file -> HighlightingLevelManager.getInstance(file.getProject()).shouldInspect(file);
  private final TextRange myPriorityRange;
  private final boolean myIgnoreSuppressed;
  private final ConcurrentMap<PsiFile, List<InspectionResult>> result = new ConcurrentHashMap<>();
  private final InspectListener myInspectTopicPublisher;
  private volatile List<HighlightInfo> myInfos = Collections.emptyList();
  private final String myShortcutText;
  private final SeverityRegistrar mySeverityRegistrar;
  private final InspectionProfileWrapper myProfileWrapper;
  private final Map<String, Set<PsiElement>> mySuppressedElements = new ConcurrentHashMap<>();
  private final boolean myInspectInjectedPsi;

  public LocalInspectionsPass(@NotNull PsiFile file,
                              @NotNull Document document,
                              int startOffset,
                              int endOffset,
                              @NotNull TextRange priorityRange,
                              boolean ignoreSuppressed,
                              @NotNull HighlightInfoProcessor highlightInfoProcessor,
                              boolean inspectInjectedPsi) {
    super(file.getProject(), document, DaemonBundle.message("pass.inspection"), file, null, new TextRange(startOffset, endOffset), true, highlightInfoProcessor);
    assert file.isPhysical() : "can't inspect non-physical file: " + file + "; " + file.getVirtualFile();
    myPriorityRange = priorityRange;
    myIgnoreSuppressed = ignoreSuppressed;
    setId(Pass.LOCAL_INSPECTIONS);

    KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager != null) {
      Keymap keymap = keymapManager.getActiveKeymap();
      myShortcutText = "(" + KeymapUtil.getShortcutsText(keymap.getShortcuts(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION)) + ")";
    }
    else {
      myShortcutText = "";
    }
    InspectionProfileImpl profileToUse = ProjectInspectionProfileManager.getInstance(myProject).getCurrentProfile();
    Function<InspectionProfileImpl, InspectionProfileWrapper> custom = file.getUserData(InspectionProfileWrapper.CUSTOMIZATION_KEY);
    myProfileWrapper = custom == null ? new InspectionProfileWrapper(profileToUse) : custom.apply(profileToUse);
    assert myProfileWrapper != null;
    mySeverityRegistrar = myProfileWrapper.getProfileManager().getSeverityRegistrar();
    myInspectInjectedPsi = inspectInjectedPsi;
    myInspectTopicPublisher = myProject.getMessageBus().syncPublisher(GlobalInspectionContextEx.INSPECT_TOPIC);

    // initial guess
    setProgressLimit(300 * 2);
  }

  private @NotNull PsiFile getFile() {
    return myFile;
  }

  @Override
  protected void collectInformationWithProgress(@NotNull ProgressIndicator progress) {
    try {
      if (!HighlightingLevelManager.getInstance(myProject).shouldInspect(getFile())) {
        return;
      }
      inspect(getInspectionTools(myProfileWrapper), InspectionManager.getInstance(myProject), true, progress);
    }
    finally {
      disposeDescriptors();
    }
  }

  private void disposeDescriptors() {
    result.clear();
  }

  private static final Set<String> ourToolsWithInformationProblems = new HashSet<>();
  public void doInspectInBatch(@NotNull GlobalInspectionContextImpl context,
                               @NotNull InspectionManager iManager,
                               @NotNull List<? extends LocalInspectionToolWrapper> toolWrappers) {
    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    inspect(new ArrayList<>(toolWrappers), iManager, false, progress);
    addDescriptorsFromInjectedResultsInBatch(context);
    List<InspectionResult> resultList = result.get(getFile());
    if (resultList == null) return;
    for (InspectionResult inspectionResult : resultList) {
      LocalInspectionToolWrapper toolWrapper = inspectionResult.tool;
      String shortName = toolWrapper.getShortName();
      List<ProblemDescriptor> toReport = new ArrayList<>(inspectionResult.foundProblems.size());
      for (ProblemDescriptor descriptor : inspectionResult.foundProblems) {
        ProblemHighlightType highlightType = descriptor.getHighlightType();
        if (highlightType == ProblemHighlightType.INFORMATION) {
          if (ourToolsWithInformationProblems.add(shortName)) {
            String message = "Tool #" + shortName + " registers INFORMATION level problem in batch mode on " + getFile() + ". " +
                             "INFORMATION level 'warnings' are invisible in the editor and should not become visible in batch mode. " +
                             "Moreover, cause INFORMATION level fixes act more like intention actions, they could e.g. change semantics and " +
                             "thus should not be suggested for batch transformations";
            LocalInspectionEP extension = toolWrapper.getExtension();
            if (extension != null) {
              LOG.error(new PluginException(message, extension.getPluginDescriptor().getPluginId()));
            }
            else {
              LOG.error(message);
            }
          }
          continue;
        }
        else if (highlightType == ProblemHighlightType.POSSIBLE_PROBLEM) {
          continue;
        }
        toReport.add(descriptor);
      }
      addDescriptors(toolWrapper, toReport, context);
    }
  }

  private void addDescriptors(@NotNull LocalInspectionToolWrapper toolWrapper,
                              @NotNull Collection<? extends ProblemDescriptor> descriptors,
                              @NotNull GlobalInspectionContextImpl context) {
    InspectionToolPresentation toolPresentation = context.getPresentation(toolWrapper);
    BatchModeDescriptorsUtil.addProblemDescriptors(descriptors, toolPresentation, myIgnoreSuppressed, context, toolWrapper.getTool());
  }

  private void addDescriptorsFromInjectedResultsInBatch(@NotNull GlobalInspectionContextImpl context) {
    for (Map.Entry<PsiFile, List<InspectionResult>> entry : result.entrySet()) {
      PsiFile file = entry.getKey();
      if (file == getFile()) continue; // not injected
      List<InspectionResult> resultList = entry.getValue();
      for (InspectionResult inspectionResult : resultList) {
        LocalInspectionToolWrapper toolWrapper = inspectionResult.tool;
        List<ProblemDescriptor> toReport = new ArrayList<>(inspectionResult.foundProblems.size());
        for (ProblemDescriptor descriptor : inspectionResult.foundProblems) {
          PsiElement psiElement = descriptor.getPsiElement();
          if (psiElement == null) continue;
          if (toolWrapper.getTool().isSuppressedFor(psiElement)) continue;
          toReport.add(descriptor);
        }
        addDescriptors(toolWrapper, toReport, context);
      }
    }
  }

  private void inspect(@NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                       @NotNull InspectionManager iManager,
                       boolean isOnTheFly,
                       @NotNull ProgressIndicator progress) {
    if (toolWrappers.isEmpty()) return;
    List<Divider.DividedElements> allDivided = new ArrayList<>();
    Divider.divideInsideAndOutsideAllRoots(myFile, myRestrictRange, myPriorityRange, SHOULD_INSPECT_FILTER, new CommonProcessors.CollectProcessor<>(allDivided));
    List<PsiElement> inside = ContainerUtil.concat((List<List<PsiElement>>)ContainerUtil.map(allDivided, d -> d.inside));
    TextRange finalPriorityRange = allDivided.isEmpty() ? myPriorityRange : allDivided.get(0).priorityRange; // might be different from myPriorityRange because DividedElements can cache not exact but containing ranges
    for (int i = 1; i < allDivided.size(); i++) {
      Divider.DividedElements dividedElements = allDivided.get(i);
      finalPriorityRange = finalPriorityRange.union(dividedElements.priorityRange);
    }
    List<PsiElement> outside = ContainerUtil.concat((List<List<PsiElement>>)ContainerUtil.map(allDivided, d -> ContainerUtil.concat(d.outside, d.parents)));

    // advance by 1 when a tool finished scanning the visible part, then the rest part, hence *2
    setProgressLimit(toolWrappers.size() * 2L);
    LocalInspectionToolSession session = new LocalInspectionToolSession(getFile(), myRestrictRange.getStartOffset(), myRestrictRange.getEndOffset());

    List<LocalInspectionToolWrapper> filteredWrappers =
      InspectionEngine.filterToolsApplicableByLanguage(toolWrappers, InspectionEngine.calcElementDialectIds(inside, outside));
    InspectionContext TOMB_STONE = InspectionContext.createTombStone(this);
    long start = System.nanoTime();
    List<InspectionContext> init = visitPriorityElementsAndInit(filteredWrappers, iManager, isOnTheFly, progress, inside, finalPriorityRange, session, TOMB_STONE);
    Set<PsiFile> alreadyVisitedInjected = inspectInjectedPsi(inside, isOnTheFly, progress, iManager, true, toolWrappers, Collections.emptySet());
    visitRestElementsAndCleanup(progress, outside, finalPriorityRange, session, init, TOMB_STONE);
    boolean isWholeFileInspectionsPass = !toolWrappers.isEmpty() && toolWrappers.get(0).getTool().runForWholeFile();
    if (isOnTheFly && !isWholeFileInspectionsPass) {
      // do not save stats for batch process, there could be too many files
      InspectionProfilerDataHolder.getInstance(myProject).saveStats(getFile(), init, System.nanoTime() - start);
    }
    reportStatsToQodana(isOnTheFly, init);
    inspectInjectedPsi(outside, isOnTheFly, progress, iManager, false, toolWrappers, alreadyVisitedInjected);
    ProgressManager.checkCanceled();

    myInfos = new ArrayList<>();
    addHighlightsFromResults(myInfos);

    if (isOnTheFly) {
      highlightRedundantSuppressions(toolWrappers, iManager, inside, outside);
    }
  }

  @NotNull
  private static Set<PsiFile> createInjectedFileSet() {
    // TODO remove when injected PsiFile implemented equals() base on its offsets in the host
    return CollectionFactory.createCustomHashingStrategySet(new HashingStrategy<>() {
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

  private void reportStatsToQodana(boolean isOnTheFly, @NotNull List<? extends InspectionContext> contexts) {
    if (!isOnTheFly) {
      for (InspectionContext context : contexts) {
        InspectionProblemsHolder holder = context.holder;
        long durationMs = TimeUnit.NANOSECONDS.toMillis(holder.finishTimeStamp - holder.initTimeStamp);
        int problemCount = context.holder.getResultCount();
        myInspectTopicPublisher.inspectionFinished(durationMs, 0, problemCount, context.tool, InspectListener.InspectionKind.LOCAL, myProject);
      }
    }
  }

  private void highlightRedundantSuppressions(@NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                                              @NotNull InspectionManager iManager,
                                              @NotNull List<? extends PsiElement> inside,
                                              @NotNull List<? extends PsiElement> outside) {
    HighlightDisplayKey key = HighlightDisplayKey.find(RedundantSuppressInspection.SHORT_NAME);
    InspectionProfile inspectionProfile = myProfileWrapper.getInspectionProfile();
    if (key != null && inspectionProfile.isToolEnabled(key, getFile())) {
      InspectionToolWrapper<?,?> toolWrapper = inspectionProfile.getInspectionTool(RedundantSuppressInspection.SHORT_NAME, getFile());
      Language fileLanguage = getFile().getLanguage();
      InspectionSuppressor suppressor = LanguageInspectionSuppressors.INSTANCE.forLanguage(fileLanguage);
      if (suppressor instanceof RedundantSuppressionDetector) {
        if (toolWrappers.stream().anyMatch(LocalInspectionToolWrapper::runForWholeFile)) {
          return;
        }
        Set<String> activeTools = new HashSet<>();
        for (LocalInspectionToolWrapper tool : toolWrappers) {
          if (tool.isUnfair() || !tool.isApplicable(fileLanguage) || myProfileWrapper.getInspectionTool(tool.getShortName(), myFile) instanceof GlobalInspectionToolWrapper) {
            continue;
          }
          activeTools.add(tool.getID());
          ContainerUtil.addIfNotNull(activeTools, tool.getAlternativeID());
          InspectionElementsMerger elementsMerger = InspectionElementsMerger.getMerger(tool.getShortName());
          if (elementsMerger != null) {
            activeTools.addAll(Arrays.asList(elementsMerger.getSuppressIds()));
          }
        }
        LocalInspectionTool
          localTool = ((RedundantSuppressInspection)toolWrapper.getTool()).createLocalTool((RedundantSuppressionDetector)suppressor, mySuppressedElements, activeTools);
        ProblemsHolder holder = new ProblemsHolder(iManager, getFile(), true);
        PsiElementVisitor visitor = localTool.buildVisitor(holder, true);
        InspectionEngine.acceptElements(inside, visitor);
        InspectionEngine.acceptElements(outside, visitor);

        HighlightSeverity severity = myProfileWrapper.getErrorLevel(key, getFile()).getSeverity();
        for (ProblemDescriptor descriptor : holder.getResults()) {
          ProgressManager.checkCanceled();
          PsiElement element = descriptor.getPsiElement();
          if (element != null) {
            Document thisDocument = Objects.requireNonNull(documentManager.getDocument(getFile()));
            createHighlightsForDescriptor(myInfos, emptyActionRegistered, ilManager, getFile(), thisDocument,
                                          new LocalInspectionToolWrapper(localTool), severity, descriptor, element, false);
          }
        }
      }
    }
  }

  private @NotNull List<InspectionContext> visitPriorityElementsAndInit(@NotNull List<? extends LocalInspectionToolWrapper> wrappers,
                                                                        @NotNull InspectionManager iManager,
                                                                        boolean isOnTheFly,
                                                                        @NotNull ProgressIndicator indicator,
                                                                        @NotNull List<? extends PsiElement> inside,
                                                                        @NotNull TextRange finalPriorityRange,
                                                                        @NotNull LocalInspectionToolSession session,
                                                                        @NotNull InspectionContext TOMB_STONE) {
    PsiFile file = session.getFile();
    List<InspectionContext> init = ContainerUtil.mapNotNull(wrappers, wrapper -> createContext(wrapper, iManager, isOnTheFly, indicator, session));

    if (InspectionProfilerDataHolder.isInspectionSortByLatencyEnabled()) {
      //sort init according to the priorities saved earlier to run in order
      InspectionProfilerDataHolder profileData = InspectionProfilerDataHolder.getInstance(myProject);
      profileData.sort(getFile(), init);
      profileData.retrieveFavoriteElements(getFile(), init);

      processInOrder(init, inside, true, finalPriorityRange, file, TOMB_STONE, indicator, context -> {
        InspectionProblemsHolder holder = context.holder;
        if (holder.hasResults()) {
          appendDescriptors(getFile(), holder.getResults(), context.tool);
        }
        holder.applyIncrementally = false; // do not apply incrementally outside visible range
      });
    }
    else {
      Processor<InspectionContext> processor = context ->
        AstLoadingFilter.disallowTreeLoading(() -> AstLoadingFilter.<Boolean, RuntimeException>forceAllowTreeLoading(file, () -> {
          oldRunToolOnElements(context.tool, inside, context);
          return true;
        }));
      if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(init, indicator, processor)) {
        throw new ProcessCanceledException();
      }
    }
    return init;
  }

  private void oldRunToolOnElements(@NotNull LocalInspectionToolWrapper toolWrapper,
                                    @NotNull List<? extends PsiElement> elements,
                                    @NotNull InspectionContext context) {
    ProgressManager.checkCanceled();

    ApplicationManager.getApplication().assertReadAccessAllowed();
    InspectionProblemsHolder holder = context.holder;

    InspectionEngine.acceptElements(elements, context.visitor);
    advanceProgress(1);

    if (holder.hasResults()) {
      appendDescriptors(getFile(), holder.getResults(), toolWrapper);
    }
    holder.applyIncrementally = false; // do not apply incrementally outside visible range
  }

  /**
   * for each tool in {@code init} (in this order) call its {@link InspectionContext#visitor} on every PsiElement in {@code inside} list (starting from this inspection's {@link InspectionContext#myFavoriteElement} if any),
   * maintaining parallelism during this process (i.e., several visitors from {@code init} can be executed concurrently, but elements from the list head get higher priority than the list tail).
   */
  private void processInOrder(@NotNull List<? extends InspectionContext> init,
                              @NotNull List<? extends PsiElement> elements,
                              boolean inside,
                              @NotNull TextRange finalPriorityRange,
                              @NotNull PsiFile file,
                              @NotNull InspectionContext TOMB_STONE,
                              @NotNull ProgressIndicator indicator,
                              @NotNull Consumer<? super InspectionContext> afterProcessCallback) {
    BlockingQueue<InspectionContext> contexts = new ArrayBlockingQueue<>(init.size() + 1, false, init);
    boolean added = contexts.offer(TOMB_STONE);
    assert added;

    boolean processed =
      ((JobLauncherImpl)JobLauncher.getInstance()).processQueue(contexts, new LinkedBlockingQueue<>(), new SensitiveProgressWrapper(indicator), TOMB_STONE, context ->
        AstLoadingFilter.disallowTreeLoading(() -> AstLoadingFilter.<Boolean, RuntimeException>forceAllowTreeLoading(file, () -> {
          ApplicationEx application = ApplicationManagerEx.getApplicationEx();
          application.executeByImpatientReader(() -> {
            if (!application.tryRunReadAction(() -> {
              InspectionProblemsHolder holder = context.holder;
              int resultCount = holder.getResultCount();
              PsiElement favoriteElement = context.myFavoriteElement;

              // accept favoriteElement only if it belongs to the correct inside/outside list
              if (favoriteElement != null && inside == favoriteElement.getTextRange().intersects(finalPriorityRange)) {
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
            })) {
              throw new ProcessCanceledException();
            }
          });
          advanceProgress(1);
          afterProcessCallback.accept(context);
          return true;
        }))
      );
    if (!processed) {
      throw new ProcessCanceledException();
    }
  }

  private InspectionContext createContext(@NotNull LocalInspectionToolWrapper toolWrapper,
                                          @NotNull InspectionManager iManager,
                                          boolean isOnTheFly,
                                          @NotNull ProgressIndicator indicator,
                                          @NotNull LocalInspectionToolSession session) {
    LocalInspectionTool tool = toolWrapper.getTool();
    InspectionProblemsHolder holder = new InspectionProblemsHolder(toolWrapper, getFile(), iManager, isOnTheFly, indicator);

    PsiElementVisitor visitor = InspectionEngine.createVisitor(tool, holder, isOnTheFly, session);
    // if inspection returned empty visitor then it should be skipped
    if (visitor != PsiElementVisitor.EMPTY_VISITOR) {
      tool.inspectionStarted(session, isOnTheFly);
      return new InspectionContext(toolWrapper, holder, visitor);
    }
    return null;
  }

  private void visitRestElementsAndCleanup(@NotNull ProgressIndicator indicator,
                                           @NotNull List<? extends PsiElement> outside,
                                           @NotNull TextRange finalPriorityRange,
                                           @NotNull LocalInspectionToolSession session,
                                           @NotNull List<? extends InspectionContext> init,
                                           @NotNull InspectionContext TOMB_STONE) {
    for (InspectionContext context : init) {
      context.problemsSizeAfterInsideElementsProcessed = context.holder.getResultCount();
    }
    PsiFile file = session.getFile();
    if (InspectionProfilerDataHolder.isInspectionSortByLatencyEnabled()) {
      processInOrder(init, outside, false, finalPriorityRange, file, TOMB_STONE, indicator, context -> {
        InspectionProblemsHolder holder = context.holder;
        holder.finishTimeStamp = System.nanoTime();
        context.tool.getTool().inspectionFinished(session, holder);

        if (holder.hasResults()) {
          List<ProblemDescriptor> allProblems = holder.getResults();
          List<ProblemDescriptor> restProblems = allProblems.subList(context.problemsSizeAfterInsideElementsProcessed, allProblems.size());
          appendDescriptors(file, restProblems, context.tool);
        }
      });
    }
    else {
      Processor<InspectionContext> processor =
        context -> {
          ProgressManager.checkCanceled();
          ApplicationManager.getApplication().assertReadAccessAllowed();
          AstLoadingFilter.disallowTreeLoading(() -> InspectionEngine.acceptElements(outside, context.visitor));
          advanceProgress(1);
          InspectionProblemsHolder holder = context.holder;
          holder.finishTimeStamp = System.nanoTime();
          context.tool.getTool().inspectionFinished(session, holder);

          if (holder.hasResults()) {
            List<ProblemDescriptor> allProblems = holder.getResults();
            List<ProblemDescriptor> restProblems = allProblems.subList(context.problemsSizeAfterInsideElementsProcessed, allProblems.size());
            appendDescriptors(file, restProblems, context.tool);
          }
          return true;
        };
      if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(init, indicator, processor)) {
        throw new ProcessCanceledException();
      }
    }
  }

  private @NotNull Set<PsiFile> inspectInjectedPsi(@NotNull List<? extends PsiElement> elements,
                                                   boolean onTheFly,
                                                   @NotNull ProgressIndicator indicator,
                                                   @NotNull InspectionManager iManager,
                                                   boolean inVisibleRange,
                                                   @NotNull List<? extends LocalInspectionToolWrapper> wrappers,
                                                   @NotNull Set<? extends PsiFile> alreadyVisitedInjected) {
    if (!myInspectInjectedPsi) return Collections.emptySet();
    Set<PsiFile> injected = createInjectedFileSet();
    PsiFile containingFile = getFile();
    Project project = containingFile.getProject();
    for (PsiElement element : elements) {
      InjectedLanguageManager.getInstance(project).enumerateEx(element, containingFile, false,
                                                               (injectedPsi, __) -> injected.add(injectedPsi));
    }
    injected.removeAll(alreadyVisitedInjected);
    if (!injected.isEmpty()) {
      Processor<PsiFile> processor = injectedPsi -> {
        doInspectInjectedPsi(injectedPsi, onTheFly, indicator, iManager, inVisibleRange, wrappers);
        return true;
      };
      if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(new ArrayList<>(injected), indicator, processor)) {
        throw new ProcessCanceledException();
      }
    }
    return injected;
  }

  private static final TextAttributes NONEMPTY_TEXT_ATTRIBUTES = new UnmodifiableTextAttributes(){
    @Override
    public boolean isEmpty() {
      return false;
    }
  };

  private @Nullable HighlightInfo highlightInfoFromDescriptor(@NotNull ProblemDescriptor problemDescriptor,
                                                              @NotNull HighlightInfoType highlightInfoType,
                                                              @NotNull @NlsContexts.DetailedDescription String message,
                                                              @Nullable @NlsContexts.Tooltip String toolTip,
                                                              @NotNull PsiElement psiElement,
                                                              @NotNull List<IntentionAction> quickFixes,
                                                              @NotNull String toolID) {
    TextRange textRange = ((ProblemDescriptorBase)problemDescriptor).getTextRange();
    if (textRange == null) return null;
    boolean isFileLevel = psiElement instanceof PsiFile && textRange.equals(psiElement.getTextRange());

    HighlightSeverity severity = highlightInfoType.getSeverity(psiElement);
    TextAttributesKey attributesKey = ((ProblemDescriptorBase)problemDescriptor).getEnforcedTextAttributes();
    TextAttributes attributes = attributesKey == null || getColorsScheme() == null
                                ? mySeverityRegistrar.getTextAttributesBySeverity(severity)
                                : getColorsScheme().getAttributes(attributesKey);
    HighlightInfo.Builder b = HighlightInfo.newHighlightInfo(highlightInfoType)
      .range(psiElement, textRange.getStartOffset(), textRange.getEndOffset())
      .description(message)
      .severity(severity)
      .inspectionToolId(toolID);
    if (toolTip != null) b.escapedToolTip(toolTip);
    if (HighlightSeverity.INFORMATION.equals(severity) && attributes == null && toolTip == null && !quickFixes.isEmpty()) {
      // Hack to avoid filtering this info out in HighlightInfoFilterImpl even though its attributes are empty.
      // But it has quick fixes, so it needs to be created.
      attributes = NONEMPTY_TEXT_ATTRIBUTES;
    }
    if (attributes != null) b.textAttributes(attributes);
    if (problemDescriptor.isAfterEndOfLine()) b.endOfLine();
    if (isFileLevel) b.fileLevelAnnotation();
    if (problemDescriptor.getProblemGroup() != null) b.problemGroup(problemDescriptor.getProblemGroup());

    return b.create();
  }

  private final Map<TextRange, RangeMarker> ranges2markersCache = new HashMap<>(); // accessed in EDT only
  private final InjectedLanguageManager ilManager = InjectedLanguageManager.getInstance(myProject);
  private final List<HighlightInfo> infos = new ArrayList<>(2); // accessed in EDT only
  private final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
  private final Set<Pair<TextRange, String>> emptyActionRegistered = Collections.synchronizedSet(new HashSet<>());

  private void addDescriptorIncrementally(@NotNull ProblemDescriptor descriptor,
                                          @NotNull LocalInspectionToolWrapper tool,
                                          @NotNull ProgressIndicator indicator) {
    if (myIgnoreSuppressed) {
      LocalInspectionToolWrapper toolWrapper = tool;
      PsiElement psiElement = descriptor.getPsiElement();
      if (descriptor instanceof ProblemDescriptorWithReporterName) {
        String reportingToolName = ((ProblemDescriptorWithReporterName)descriptor).getReportingToolName();
        toolWrapper = (LocalInspectionToolWrapper)myProfileWrapper.getInspectionTool(reportingToolName, psiElement);
      }

      if (toolWrapper.getTool().isSuppressedFor(psiElement)) {
        registerSuppressedElements(psiElement, toolWrapper.getID(), toolWrapper.getAlternativeID());
        return;
      }
    }

    ApplicationManager.getApplication().invokeLater(()->{
      PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement == null) return;
      PsiFile file = psiElement.getContainingFile();
      Document thisDocument = Objects.requireNonNull(documentManager.getDocument(file));

      HighlightSeverity severity = myProfileWrapper.getErrorLevel(tool.getDisplayKey(), file).getSeverity();

      infos.clear();
      createHighlightsForDescriptor(infos, emptyActionRegistered, ilManager, file, thisDocument, tool, severity, descriptor, psiElement);
      for (HighlightInfo info : infos) {
        EditorColorsScheme colorsScheme = getColorsScheme();
        UpdateHighlightersUtil.addHighlighterToEditorIncrementally(myProject, myDocument, getFile(),
                                                                   myRestrictRange.getStartOffset(),
                                                                   myRestrictRange.getEndOffset(),
                                                                   info, colorsScheme, getId(),
                                                                   ranges2markersCache);
      }
    }, __->myProject.isDisposed() || indicator.isCanceled());
  }

  private void appendDescriptors(@NotNull PsiFile file, @NotNull List<? extends ProblemDescriptor> descriptors, @NotNull LocalInspectionToolWrapper tool) {
    for (ProblemDescriptor descriptor : descriptors) {
      if (descriptor == null) {
        LOG.error("null descriptor. all descriptors(" + descriptors.size() +"): " +
                  descriptors + "; file: " + file + " (" + file.getVirtualFile() +"); tool: " + tool);
      }
    }
    InspectionResult result = new InspectionResult(tool, descriptors);
    appendResult(file, result);
  }

  private void appendResult(@NotNull PsiFile file, @NotNull InspectionResult result) {
    List<InspectionResult> resultList = this.result.get(file);
    if (resultList == null) {
      resultList = ConcurrencyUtil.cacheOrGet(this.result, file, new ArrayList<>());
    }
    synchronized (resultList) {
      resultList.add(result);
    }
  }

  @Override
  protected void applyInformationWithProgress() {
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myRestrictRange.getStartOffset(), myRestrictRange.getEndOffset(), myInfos, getColorsScheme(), getId());
  }

  private void addHighlightsFromResults(@NotNull List<? super HighlightInfo> outInfos) {
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    InjectedLanguageManager ilManager = InjectedLanguageManager.getInstance(myProject);
    Set<Pair<TextRange, String>> emptyActionRegistered = new HashSet<>();

    for (Map.Entry<PsiFile, List<InspectionResult>> entry : result.entrySet()) {
      ProgressManager.checkCanceled();
      PsiFile file = entry.getKey();
      Document documentRange = documentManager.getDocument(file);
      if (documentRange == null) continue;
      List<InspectionResult> resultList = entry.getValue();
      synchronized (resultList) {
        for (InspectionResult inspectionResult : resultList) {
          ProgressManager.checkCanceled();
          LocalInspectionToolWrapper tool = inspectionResult.tool;
          HighlightSeverity severity = myProfileWrapper.getErrorLevel(tool.getDisplayKey(), file).getSeverity();
          for (ProblemDescriptor descriptor : inspectionResult.foundProblems) {
            ProgressManager.checkCanceled();
            PsiElement element = descriptor.getPsiElement();
            if (element != null) {
              createHighlightsForDescriptor(outInfos, emptyActionRegistered, ilManager, file, documentRange, tool, severity, descriptor, element,
                                            myIgnoreSuppressed);
            }
          }
        }
      }
    }
  }

  private void createHighlightsForDescriptor(@NotNull List<? super HighlightInfo> outInfos,
                                             @NotNull Set<? super Pair<TextRange, String>> emptyActionRegistered,
                                             @NotNull InjectedLanguageManager ilManager,
                                             @NotNull PsiFile file,
                                             @NotNull Document documentRange,
                                             @NotNull LocalInspectionToolWrapper toolWrapper,
                                             @NotNull HighlightSeverity severity,
                                             @NotNull ProblemDescriptor descriptor,
                                             @NotNull PsiElement element,
                                             boolean ignoreSuppressed) {
    if (descriptor instanceof ProblemDescriptorWithReporterName) {
      String reportingToolName = ((ProblemDescriptorWithReporterName)descriptor).getReportingToolName();
      InspectionToolWrapper<?, ?> reportingTool = myProfileWrapper.getInspectionTool(reportingToolName, element);
      LOG.assertTrue(reportingTool instanceof LocalInspectionToolWrapper, reportingToolName);
      toolWrapper = (LocalInspectionToolWrapper)reportingTool;
      severity = myProfileWrapper.getErrorLevel(HighlightDisplayKey.find(reportingToolName), file).getSeverity();
    }
    LocalInspectionTool tool = toolWrapper.getTool();
    if (ignoreSuppressed && tool.isSuppressedFor(element)) {
      registerSuppressedElements(element, toolWrapper.getID(), toolWrapper.getAlternativeID());
      return;
    }
    createHighlightsForDescriptor(outInfos, emptyActionRegistered, ilManager, file, documentRange, toolWrapper, severity, descriptor, element);
  }

  private void createHighlightsForDescriptor(@NotNull List<? super HighlightInfo> outInfos,
                                             @NotNull Set<? super Pair<TextRange, String>> emptyActionRegistered,
                                             @NotNull InjectedLanguageManager ilManager,
                                             @NotNull PsiFile file,
                                             @NotNull Document documentRange,
                                             @NotNull LocalInspectionToolWrapper toolWrapper,
                                             @NotNull HighlightSeverity severity,
                                             @NotNull ProblemDescriptor descriptor,
                                             @NotNull PsiElement element) {
    HighlightInfoType level = ProblemDescriptorUtil.highlightTypeFromDescriptor(descriptor, severity, mySeverityRegistrar);
    ProblemPresentation presentation = ProblemDescriptorUtil.renderDescriptor(descriptor, element, ProblemDescriptorUtil.NONE);
    String message = presentation.getDescription();

    ProblemGroup problemGroup = descriptor.getProblemGroup();
    String problemName = problemGroup != null ? problemGroup.getProblemName() : null;
    String shortName = problemName != null ? problemName : toolWrapper.getShortName();
    HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    InspectionProfile inspectionProfile = myProfileWrapper.getInspectionProfile();
    if (!inspectionProfile.isToolEnabled(key, getFile())) return;

    HighlightInfoType type = new InspectionHighlightInfoType(level, element);
    String plainMessage = message.startsWith("<html>")
                          ? StringUtil.unescapeXmlEntities(XmlStringUtil.stripHtml(message).replaceAll("<[^>]*>", ""))
                            .replaceAll("&nbsp;", " ")
                          : message;

    @NlsSafe String tooltip = null;
    if (descriptor.showTooltip()) {
      String rendered = presentation.getTooltip();
      tooltip = tooltips.intern(DaemonTooltipsUtil.getWrappedTooltip(rendered, shortName, myShortcutText, showToolDescription(toolWrapper)));
    }
    List<IntentionAction> fixes = getQuickFixes(key, descriptor, emptyActionRegistered);
    HighlightInfo info = highlightInfoFromDescriptor(descriptor, type, plainMessage, tooltip, element, fixes, key.getID());
    if (info == null) return;
    registerQuickFixes(info, fixes, shortName);

    PsiFile context = getTopLevelFileInBaseLanguage(element);
    PsiFile myContext = getTopLevelFileInBaseLanguage(getFile());
    if (context != getFile()) {
      String errorMessage = "Reported element " + element +
                            " is not from the file '" + file.getVirtualFile().getPath() +
                            "' the inspection '" + shortName +
                            "' (" + toolWrapper.getTool().getClass() +
                            ") was invoked for. Message: '" + descriptor + "'.\nElement containing file: " +
                            context + "\nInspection invoked for file: " + myContext + "\n";
      PluginException.logPluginError(LOG, errorMessage, null, toolWrapper.getTool().getClass());
    }
    boolean isOutsideInjected = !myInspectInjectedPsi || file == getFile();
    if (isOutsideInjected) {
      outInfos.add(info);
    }
    else {
      injectToHost(outInfos, ilManager, file, documentRange, element, fixes, info, shortName);
    }
  }

  private void registerSuppressedElements(@NotNull PsiElement element, @NotNull String id, @Nullable String alternativeID) {
    mySuppressedElements.computeIfAbsent(id, __ -> new HashSet<>()).add(element);
    if (alternativeID != null) {
      mySuppressedElements.computeIfAbsent(alternativeID, __ -> new HashSet<>()).add(element);
    }
  }

  private static void injectToHost(@NotNull List<? super HighlightInfo> outInfos,
                                   @NotNull InjectedLanguageManager ilManager,
                                   @NotNull PsiFile file,
                                   @NotNull Document documentRange,
                                   @NotNull PsiElement element,
                                   @NotNull List<? extends IntentionAction> fixes,
                                   @NotNull HighlightInfo info,
                                   @NotNull String shortName) {
    // todo we got to separate our "internal" prefixes/suffixes from user-defined ones
    // todo in the latter case the errors should be highlighted, otherwise not
    List<TextRange> editables = ilManager.intersectWithAllEditableFragments(file, new TextRange(info.startOffset, info.endOffset));
    for (TextRange editable : editables) {
      TextRange hostRange = ((DocumentWindow)documentRange).injectedToHost(editable);
      int start = hostRange.getStartOffset();
      int end = hostRange.getEndOffset();
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(info.type).range(element, start, end);
      String description = info.getDescription();
      if (description != null) {
        builder.description(description);
      }
      String toolTip = info.getToolTip();
      if (toolTip != null) {
        builder.escapedToolTip(toolTip);
      }
      HighlightInfo patched = builder.createUnconditionally();
      if (patched.startOffset != patched.endOffset || info.startOffset == info.endOffset) {
        patched.setFromInjection(true);
        registerQuickFixes(patched, fixes, shortName);
        outInfos.add(patched);
      }
    }
  }

  private PsiFile getTopLevelFileInBaseLanguage(@NotNull PsiElement element) {
    PsiFile file = InjectedLanguageManager.getInstance(myProject).getTopLevelFile(element);
    FileViewProvider viewProvider = file.getViewProvider();
    return viewProvider.getPsi(viewProvider.getBaseLanguage());
  }


  private static final Interner<String> tooltips = Interner.createWeakInterner();

  private static boolean showToolDescription(@NotNull LocalInspectionToolWrapper tool) {
    String staticDescription = tool.getStaticDescription();
    return staticDescription == null || !staticDescription.isEmpty();
  }

  private static void registerQuickFixes(@NotNull HighlightInfo highlightInfo,
                                         @NotNull List<? extends IntentionAction> quickFixes,
                                         @NotNull String shortName) {
    HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    for (IntentionAction quickFix : quickFixes) {
      QuickFixAction.registerQuickFixAction(highlightInfo, quickFix, key);
    }
  }

  private static @NotNull List<IntentionAction> getQuickFixes(@NotNull HighlightDisplayKey key,
                                                              @NotNull ProblemDescriptor descriptor,
                                                              @NotNull Set<? super Pair<TextRange, String>> emptyActionRegistered) {
    List<IntentionAction> result = new SmartList<>();
    boolean needEmptyAction = true;
    QuickFix<?>[] fixes = descriptor.getFixes();
    if (fixes != null && fixes.length != 0) {
      for (int k = 0; k < fixes.length; k++) {
        QuickFix<?> fix = fixes[k];
        if (fix == null) {
          throw new IllegalStateException("Inspection " + key + " returns null quick fix in its descriptor: " + descriptor + "; array: " + Arrays.toString(fixes));
        }
        result.add(QuickFixWrapper.wrap(descriptor, k));
        needEmptyAction = false;
      }
    }
    HintAction hintAction = descriptor instanceof ProblemDescriptorImpl ? ((ProblemDescriptorImpl)descriptor).getHintAction() : null;
    if (hintAction != null) {
      result.add(hintAction);
      needEmptyAction = false;
    }
    if (((ProblemDescriptorBase)descriptor).getEnforcedTextAttributes() != null) {
      needEmptyAction = false;
    }
    if (needEmptyAction && emptyActionRegistered.add(Pair.create(((ProblemDescriptorBase)descriptor).getTextRange(), key.toString()))) {
      String displayNameByKey = HighlightDisplayKey.getDisplayNameByKey(key);
      LOG.assertTrue(displayNameByKey != null, key.toString());
      IntentionAction emptyIntentionAction = new EmptyIntentionAction(displayNameByKey);
      result.add(emptyIntentionAction);
    }
    return result;
  }

  private static void getElementsAndDialectsFrom(@NotNull PsiFile file,
                                                 @NotNull List<? super PsiElement> outElements,
                                                 @NotNull Set<? super String> outDialects) {
    FileViewProvider viewProvider = file.getViewProvider();
    Set<Language> processedLanguages = new SmartHashSet<>();
    PsiElementVisitor visitor = new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        ProgressManager.checkCanceled();
        PsiElement child = element.getFirstChild();
        while (child != null) {
          outElements.add(child);
          child.accept(this);
          appendDialects(child, processedLanguages, outDialects);
          child = child.getNextSibling();
        }
      }
    };
    for (Language language : viewProvider.getLanguages()) {
      PsiFile psiRoot = viewProvider.getPsi(language);
      if (psiRoot == null || !HighlightingLevelManager.getInstance(file.getProject()).shouldInspect(psiRoot)) {
        continue;
      }
      outElements.add(psiRoot);
      psiRoot.accept(visitor);
      appendDialects(psiRoot, processedLanguages, outDialects);
    }
  }

  private static void appendDialects(@NotNull PsiElement element,
                                     @NotNull Set<? super Language> outProcessedLanguages,
                                     @NotNull Set<? super String> outDialectIds) {
    Language language = element.getLanguage();
    outDialectIds.add(language.getID());
    if (outProcessedLanguages.add(language)) {
      for (Language dialect : language.getDialects()) {
        outDialectIds.add(dialect.getID());
      }
    }
  }

  @NotNull
  List<LocalInspectionToolWrapper> getInspectionTools(@NotNull InspectionProfileWrapper profile) {
    List<InspectionToolWrapper<?, ?>> toolWrappers = profile.getInspectionProfile().getInspectionTools(getFile());
    InspectionProfileWrapper.checkInspectionsDuplicates(toolWrappers);
    List<LocalInspectionToolWrapper> enabled = new ArrayList<>();
    for (InspectionToolWrapper<?, ?> toolWrapper : toolWrappers) {
      ProgressManager.checkCanceled();
      if (toolWrapper instanceof LocalInspectionToolWrapper && !isAcceptableLocalTool((LocalInspectionToolWrapper)toolWrapper)) {
        continue;
      }
      HighlightDisplayKey key = toolWrapper.getDisplayKey();
      if (!profile.isToolEnabled(key, getFile())) continue;
      if (HighlightDisplayLevel.DO_NOT_SHOW.equals(profile.getErrorLevel(key, getFile()))) continue;
      LocalInspectionToolWrapper wrapper;
      if (toolWrapper instanceof LocalInspectionToolWrapper) {
        wrapper = (LocalInspectionToolWrapper)toolWrapper;
      }
      else {
        wrapper = ((GlobalInspectionToolWrapper)toolWrapper).getSharedLocalInspectionToolWrapper();
        if (wrapper == null || !isAcceptableLocalTool(wrapper)) continue;
      }
      String language = wrapper.getLanguage();
      if (language != null && Language.findLanguageByID(language) == null) {
        continue; // filter out at least unknown languages
      }
      if (myIgnoreSuppressed && wrapper.getTool().isSuppressedFor(getFile())) {
        continue;
      }
      enabled.add(wrapper);
    }
    return enabled;
  }

  protected boolean isAcceptableLocalTool(@NotNull LocalInspectionToolWrapper wrapper) {
    return true;
  }

  private void doInspectInjectedPsi(@NotNull PsiFile injectedPsi,
                                    boolean isOnTheFly,
                                    @NotNull ProgressIndicator indicator,
                                    @NotNull InspectionManager iManager,
                                    boolean inVisibleRange,
                                    @NotNull List<? extends LocalInspectionToolWrapper> wrappers) {
    PsiElement host = InjectedLanguageManager.getInstance(injectedPsi.getProject()).getInjectionHost(injectedPsi);

    List<PsiElement> elements = new ArrayList<>();
    Set<String> elementDialectIds = new SmartHashSet<>();
    getElementsAndDialectsFrom(injectedPsi, elements, elementDialectIds);
    if (elements.isEmpty()) {
      return;
    }
    List<LocalInspectionToolWrapper> applicableTools = InspectionEngine.filterToolsApplicableByLanguage(wrappers, elementDialectIds);
    for (LocalInspectionToolWrapper wrapper : applicableTools) {
      ProgressManager.checkCanceled();
      LocalInspectionTool tool = wrapper.getTool();
      ProblemsHolder holder = new ProblemsHolder(iManager, injectedPsi, isOnTheFly) {
        @Override
        public void registerProblem(@NotNull ProblemDescriptor descriptor) {
          if (host != null && myIgnoreSuppressed && tool.isSuppressedFor(host)) {
            registerSuppressedElements(host, wrapper.getID(), wrapper.getAlternativeID());
            return;
          }
          super.registerProblem(descriptor);
          if (isOnTheFly && inVisibleRange) {
            addDescriptorIncrementally(descriptor, wrapper, indicator);
          }
        }
      };

      LocalInspectionToolSession injSession = new LocalInspectionToolSession(injectedPsi, 0, injectedPsi.getTextLength());
      InspectionEngine.createVisitorAndAcceptElements(tool, holder, isOnTheFly, injSession, elements);
      tool.inspectionFinished(injSession, holder);
      List<ProblemDescriptor> problems = holder.getResults();
      if (!problems.isEmpty()) {
        appendDescriptors(injectedPsi, problems, wrapper);
      }
    }
  }

  @Override
  public @NotNull List<HighlightInfo> getInfos() {
    return myInfos;
  }

  private static final class InspectionResult {
    private final @NotNull LocalInspectionToolWrapper tool;
    private final @NotNull List<? extends ProblemDescriptor> foundProblems;

    private InspectionResult(@NotNull LocalInspectionToolWrapper tool, @NotNull List<? extends ProblemDescriptor> foundProblems) {
      this.tool = tool;
      this.foundProblems = new ArrayList<>(foundProblems);
    }
  }

  static class InspectionContext {
    private InspectionContext(@NotNull LocalInspectionToolWrapper tool, @NotNull InspectionProblemsHolder holder, @NotNull PsiElementVisitor visitor) {
      this.tool = tool;
      this.holder = holder;
      this.visitor = visitor;
    }

    final @NotNull LocalInspectionToolWrapper tool;
    final @NotNull InspectionProblemsHolder holder;
    volatile int problemsSizeAfterInsideElementsProcessed;
    final @NotNull PsiElementVisitor visitor;
    volatile PsiElement myFavoriteElement; // the element during visiting which some diagnostics were generated in previous run

    @NotNull
    static InspectionContext createTombStone(@NotNull LocalInspectionsPass pass) {
      LocalInspectionToolWrapper tool = new LocalInspectionToolWrapper(new LocalInspectionEP());
      InspectionProblemsHolder holder = pass.new InspectionProblemsHolder(tool, pass.getFile(), InspectionManager.getInstance(pass.getFile().getProject()), false, new EmptyProgressIndicator());
      return new InspectionContext(tool, holder, new PsiElementVisitor() {});
    }
  }

  public static class InspectionHighlightInfoType extends HighlightInfoType.HighlightInfoTypeImpl {
    InspectionHighlightInfoType(@NotNull HighlightInfoType level, @NotNull PsiElement element) {
      super(level.getSeverity(element), level.getAttributesKey());
    }
  }

  class InspectionProblemsHolder extends ProblemsHolder {
    private @NotNull final LocalInspectionToolWrapper myToolWrapper;
    private @NotNull final ProgressIndicator myIndicator;
    private volatile boolean applyIncrementally = true;
    long errorStamp; // nano-stamp of the first error created
    long warningStamp; // nano-stamp of the first warning created
    long otherStamp; // nano-stamp of the first info/weak warn/etc. created
    final long initTimeStamp = System.nanoTime();
    volatile long finishTimeStamp;

    InspectionProblemsHolder(@NotNull LocalInspectionToolWrapper toolWrapper,
                             @NotNull PsiFile file,
                             @NotNull InspectionManager iManager,
                             boolean isOnTheFly,
                             @NotNull ProgressIndicator indicator) {
      super(iManager, file, isOnTheFly);
      myToolWrapper = toolWrapper;
      myIndicator = indicator;
    }

    @Override
    public void registerProblem(@NotNull ProblemDescriptor descriptor) {
      super.registerProblem(descriptor);
      if (applyIncrementally) {
        addDescriptorIncrementally(descriptor, myToolWrapper, myIndicator);
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
  }
}
