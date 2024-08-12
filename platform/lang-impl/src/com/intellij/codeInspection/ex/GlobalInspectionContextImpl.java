// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.analysis.problemsView.toolWindow.ProblemsView;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor;
import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.HighlightingSessionImpl;
import com.intellij.codeInsight.daemon.impl.ProblemDescriptorWithReporterName;
import com.intellij.codeInsight.util.GlobalInspectionScopeKt;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.actions.CleanupInspectionUtil;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.DefaultInspectionToolPresentation;
import com.intellij.codeInspection.ui.DelegatedInspectionToolPresentation;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.concurrency.JobLauncher;
import com.intellij.concurrency.JobLauncherImpl;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.IntEventField;
import com.intellij.internal.statistic.eventLog.events.LongEventField;
import com.intellij.internal.statistic.eventLog.events.RoundedIntEventField;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.lang.LangBundle;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceKt;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import io.opentelemetry.api.trace.Span;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * To create an instance, see {@link InspectionManager#createNewGlobalContext()}
 */
@ApiStatus.Internal
public class GlobalInspectionContextImpl extends GlobalInspectionContextEx {
  private static final Logger LOG = Logger.getInstance(GlobalInspectionContextImpl.class);

  @SuppressWarnings("StaticNonFinalField")
  @TestOnly
  public static volatile boolean TESTING_VIEW;

  public static final String NOTIFICATION_GROUP = "Inspection Results";

  private final NotNullLazyValue<? extends ContentManager> myContentManager;
  private volatile InspectionResultsView myView;
  private Content myContent;
  protected volatile boolean myViewClosed = true;
  private long myInspectionStartedTimestamp;
  private Span runToolsSpan;
  private final ConcurrentMap<InspectionToolWrapper<?, ?>, InspectionToolPresentation> myPresentationMap = new ConcurrentHashMap<>();

  public GlobalInspectionContextImpl(@NotNull Project project, @NotNull NotNullLazyValue<? extends ContentManager> contentManager) {
    super(project);

    myContentManager = contentManager;
  }

  protected @NotNull InspectListener getInspectionEventPublisher() {
    return getProject().getMessageBus().syncPublisher(INSPECT_TOPIC);
  }

  private @NotNull ContentManager getContentManager() {
    return myContentManager.getValue();
  }

  public void addView(
    @NotNull InspectionResultsView view,
    @NotNull @NlsContexts.TabTitle String title,
    boolean isOffline
  ) {
    if (myContent != null) {
      LOG.error("GlobalInspectionContext is busy under other view: " + myContent.getDisplayName());
    }
    myView = view;
    if (!isOffline) {
      myView.setUpdating(true);
    }
    myContent = ContentFactory.getInstance().createContent(view, title, false);
    myContent.setHelpId(InspectionResultsView.HELP_ID);
    myContent.setDisposer(myView);
    Disposer.register(myContent, () -> {
      if (myView != null) {
        close(false);
      }
      myContent = null;
    });
    RefManagerImpl.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionRemoved(@NotNull RefGraphAnnotator graphAnnotator, @NotNull PluginDescriptor pluginDescriptor) {
        ((RefManagerImpl)getRefManager()).unregisterAnnotator(graphAnnotator);
      }
    }, myContent);

    ContentManager contentManager = getContentManager();
    contentManager.addContent(myContent);
    contentManager.setSelectedContent(myContent);

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(getProject());
    ToolWindow toolWindow = toolWindowManager.getToolWindow(ProblemsView.ID);
    if (toolWindow != null) {
      view.initAdditionalGearActions(toolWindow);
      toolWindow.activate(null);
    }
  }

  public void addView(@NotNull InspectionResultsView view) {
    addView(view, view.getViewTitle(), false);
  }

  @Override
  public void doInspections(@NotNull AnalysisScope scope) {
    if (myContent != null) {
      getContentManager().removeContent(myContent, true);
    }
    super.doInspections(scope);
  }

  public void resolveElement(@NotNull InspectionProfileEntry tool, @NotNull PsiElement element) {
    RefElement refElement = getRefManager().getReference(element);
    if (refElement == null) return;
    Tools tools = getTools().get(tool.getShortName());
    if (tools != null) {
      for (ScopeToolState state : tools.getTools()) {
        InspectionToolWrapper<?, ?> toolWrapper = state.getTool();
        InspectionToolResultExporter presentation = getPresentationOrNull(toolWrapper);
        if (presentation != null) {
          resolveElementRecursively(presentation, refElement);
        }
      }
    }
  }

  public InspectionResultsView getView() {
    return myView;
  }

  private static void resolveElementRecursively(@NotNull InspectionToolResultExporter presentation, @NotNull RefEntity refElement) {
    presentation.suppressProblem(refElement);
    if (refElement instanceof RefElement) ((RefElement)refElement).initializeIfNeeded();
    List<RefEntity> children = refElement.getChildren();
    for (RefEntity child : children) {
      resolveElementRecursively(presentation, child);
    }
  }

  public @NotNull AnalysisUIOptions getUIOptions() {
    return AnalysisUIOptions.getInstance(getProject());
  }

  public void setSplitterProportion(float proportion) {
    getUIOptions().SPLITTER_PROPORTION = proportion;
  }

  public @NotNull ToggleAction createToggleAutoscrollAction() {
    return getUIOptions().getAutoScrollToSourceHandler().createToggleAction();
  }

  @Override
  protected void launchInspections(@NotNull AnalysisScope scope) {
    myViewClosed = false;
    super.launchInspections(scope);
  }

  @Override
  @SuppressWarnings("deprecation")
  protected @NotNull PerformInBackgroundOption createOption() {
    return new PerformAnalysisInBackgroundOption(getProject());
  }

  @Override
  protected void notifyInspectionsFinished(@NotNull AnalysisScope scope) {
    //noinspection TestOnlyProblems
    if (ApplicationManager.getApplication().isUnitTestMode() && !TESTING_VIEW) return;
    ThreadingAssertions.assertEventDispatchThread();
    long elapsed = System.currentTimeMillis() - myInspectionStartedTimestamp;
    runToolsSpan.end();
    LOG.info("Code inspection finished. Took " + elapsed + " ms");
    if (getProject().isDisposed()) return;

    InspectionResultsView oldView = myView;
    InspectionResultsView newView = oldView == null ? new InspectionResultsView(this, new InspectionRVContentProviderImpl()) : null;
    if (newView != null) Disposer.register(getProject(), newView);
    ReadAction
      .nonBlocking(() -> (oldView == null ? newView : oldView).hasProblems())
      .finishOnUiThread(ModalityState.any(), hasProblems -> {
        if (!hasProblems) {
          showNoProblemNotification(scope, newView);
        }
        else if (newView != null && !newView.isDisposed() && getCurrentScope() != null) {
          addView(newView);
          newView.update();
        }
        if (myView != null) {
          myView.setUpdating(false);
        }
      })
      .expireWith(getProject())
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private void showNoProblemNotification(@NotNull AnalysisScope scope, InspectionResultsView newView) {
    int totalFiles = getStdJobDescriptors().BUILD_GRAPH.getTotalAmount(); // do not use invalidated scope

    var notification = new Notification(
      NOTIFICATION_GROUP,
      InspectionsBundle.message("inspection.no.problems.message", totalFiles, scope.getShortenName()),
      NotificationType.INFORMATION);
    if (!scope.isIncludeTestSource()) addRepeatWithTestsAction(scope, notification, () -> doInspections(scope));
    notification.notify(getProject());

    close(true);
    if (newView != null) {
      Disposer.dispose(newView);
    }
  }

  @Override
  @RequiresBackgroundThread
  protected void runTools(@NotNull AnalysisScope scope, boolean runGlobalToolsOnly, boolean isOfflineInspections) {
    IJTracer tracer = TelemetryManager.getInstance().getTracer(GlobalInspectionScopeKt.GlobalInspectionScope);
    runToolsSpan = tracer.spanBuilder("globalInspections").setNoParent().startSpan();
    myInspectionStartedTimestamp = System.currentTimeMillis();
    ProgressIndicator progressIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (!(progressIndicator instanceof ProgressIndicatorWithDelayedPresentation)) {
      throw new IncorrectOperationException("Must be run under ProgressWindow");
    }
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      throw new IncorrectOperationException("Must not start inspections from within write action");
    }
    // in offline inspection application we don't care about global read action
    if (!isOfflineInspections && ApplicationManager.getApplication().isReadAccessAllowed()) {
      throw new IncorrectOperationException("Must not start inspections from within global read action");
    }
    InspectionManager inspectionManager = InspectionManager.getInstance(getProject());
    ((RefManagerImpl)getRefManager()).initializeAnnotators();
    List<Tools> globalTools = new ArrayList<>();
    List<Tools> localTools = new ArrayList<>();
    List<Tools> globalSimpleTools = new ArrayList<>();
    initializeTools(globalTools, localTools, globalSimpleTools);

    runGlobalTools(scope, inspectionManager, globalTools, isOfflineInspections);
    TraceKt.use(tracer.spanBuilder("externalInspectionsAnalysis"), __ -> {
      runExternalTools();
      return null;
    });

    if (runGlobalToolsOnly || localTools.isEmpty() && globalSimpleTools.isEmpty()) return;

    SearchScope searchScope = ReadAction.compute(scope::toSearchScope);
    Set<VirtualFile> localScopeFiles = searchScope instanceof LocalSearchScope ? new HashSet<>() : null;
    for (Tools tools : globalSimpleTools) {
      GlobalInspectionToolWrapper toolWrapper = (GlobalInspectionToolWrapper)tools.getTool();
      if (toolWrapper.getTool() instanceof  GlobalSimpleInspectionTool tool) {
        tool.inspectionStarted(inspectionManager, this, getPresentation(toolWrapper));
      }
    }

    boolean headlessEnvironment = ApplicationManager.getApplication().isHeadlessEnvironment();

    Map<String, InspectionToolWrapper<?, ?>> map = getInspectionWrappersMap(localTools);

    BlockingQueue<VirtualFile> filesToInspect = new ArrayBlockingQueue<>(1000);
    // use the original progress indicator here since we don't want it to cancel on write action start
    ProgressIndicator fileScanningIndicator = new SensitiveProgressWrapper(progressIndicator);
    Future<?> future = startIterateScopeInBackground(scope, fileScanningIndicator, headlessEnvironment, localScopeFiles, filesToInspect);

    var enabledInspectionsProvider = createEnabledInspectionsProvider(localTools, globalSimpleTools, getProject());
    Processor<VirtualFile> processor = buildProcessor(scope, enabledInspectionsProvider, searchScope, inspectionManager, map);
    var localInspectionsSpan = tracer.spanBuilder("localInspectionsAnalysis").startSpan();
    try {
      Queue<VirtualFile> filesFailedToInspect = new LinkedBlockingQueue<>();
      while (true) {
        Disposable disposable = Disposer.newDisposable();
        ProgressIndicator wrapper = new DaemonProgressIndicator();
        dependentIndicators.add(wrapper);
        try {
          setupCancelOnWriteProgress(disposable, wrapper);
          // use wrapper here to cancel early when write action start but do not affect the original indicator
          ((JobLauncherImpl)JobLauncher.getInstance()).processQueue(filesToInspect, filesFailedToInspect, wrapper, TOMBSTONE, processor);
          break;
        }
        catch (ProcessCanceledException e) {
          progressIndicator.checkCanceled();
          // PCE may be thrown from inside wrapper when write action started.
          // Go on with the write action and then resume processing the rest of the queue.
          if (!isOfflineInspections) {
            ApplicationManager.getApplication().assertReadAccessNotAllowed();
            ApplicationManager.getApplication().assertIsNonDispatchThread();
          }

          // wait for write action to complete
          ApplicationManager.getApplication().runReadAction(EmptyRunnable.getInstance());
        }
        finally {
          dependentIndicators.remove(wrapper);
          Disposer.dispose(disposable);
        }
      }
    }
    finally {
      fileScanningIndicator.cancel(); // tell file scanning thread to stop
      filesToInspect.clear(); // let file scanning thread a chance to put TOMBSTONE and complete
      try {
        future.get(30, TimeUnit.SECONDS);
      }
      catch (Exception e) {
        LOG.error("Thread dump: \n" + ThreadDumper.dumpThreadsToString(), e);
      }
      localInspectionsSpan.end();
    }

    ProgressManager.checkCanceled();

    for (Tools tools : globalSimpleTools) {
      GlobalInspectionToolWrapper toolWrapper = (GlobalInspectionToolWrapper)tools.getTool();
      if (toolWrapper.getTool() instanceof GlobalSimpleInspectionTool tool) {
        ProblemDescriptionsProcessor problemDescriptionProcessor = getProblemDescriptionProcessor(toolWrapper, map);
        tool.inspectionFinished(inspectionManager, this, problemDescriptionProcessor);
      }
    }

    addProblemsToView(globalSimpleTools);
  }

  private @NotNull Processor<VirtualFile> buildProcessor(@NotNull AnalysisScope scope,
                                                         EnabledInspectionsProvider enabledInspectionsProvider,
                                                         SearchScope searchScope,
                                                         InspectionManager inspectionManager,
                                                         Map<String, InspectionToolWrapper<?, ?>> map) {
    PsiManager psiManager = PsiManager.getInstance(getProject());
    boolean inspectInjectedPsi = Registry.is("idea.batch.inspections.inspect.injected.psi", true);

    return virtualFile -> {
      ProgressManager.checkCanceled();

      final var wrappersForThisFile = new AtomicReference<EnabledInspectionsProvider.ToolWrappers>(null);

      Computable<Boolean> inspection = () -> {
        long start = getPathProfile() == null ? 0 : System.currentTimeMillis();
        PsiFile file = virtualFile.isValid() ? psiManager.findFile(virtualFile) : null;
        if (file == null) {
          return true;
        }
        if (!scope.contains(virtualFile)) {
          LOG.info(file.getName() + "; scope: " + scope + "; " + virtualFile);
          return true;
        }
        boolean includeDoNotShow = includeDoNotShow(getCurrentProfile());
        var wrappers = getWrappersFromTools(enabledInspectionsProvider, file, includeDoNotShow);
        wrappersForThisFile.set(wrappers);

        inspectFile(file, getEffectiveRange(searchScope, file), inspectionManager, map,
                    wrappers.getGlobalSimpleRegularWrappers(),
                    wrappers.getLocalRegularWrappers(),
                    inspectInjectedPsi && scope.isAnalyzeInjectedCode());
        if (start != 0) {
          updateProfile(virtualFile, System.currentTimeMillis() - start);
        }
        return true;
      };
      Boolean readActionSuccess = DumbService.getInstance(getProject())
        .tryRunReadActionInSmartMode(
          inspection,
          LangBundle.message("popup.content.inspect.code.not.available.until.indices.are.ready"),
          DumbModeBlockedFunctionality.GlobalInspectionContext
        );

      if (readActionSuccess == null || !readActionSuccess) {
        throw new ProcessCanceledException();
      }

      PsiFile file = ReadAction.compute(() -> psiManager.findFile(virtualFile));
      if (file == null) {
        return true;
      }
      getInspectionEventPublisher().fileAnalyzed(file, getProject());

      boolean includeDoNotShow = includeDoNotShow(getCurrentProfile());

      var cachedWrappersValue = wrappersForThisFile.get();
      var wrappers =
        (cachedWrappersValue != null) ? cachedWrappersValue : getWrappersFromTools(enabledInspectionsProvider, file, includeDoNotShow);

      wrappers.getExternalAnnotatorWrappers()
        .forEach(wrapper -> {
          var tool = ((ExternalAnnotatorBatchInspection)wrapper.getTool());
          var descriptors = tool.checkFile(file, this, inspectionManager);
          InspectionToolResultExporter toolPresentation = getPresentation(wrapper);
          ReadAction.run(() -> BatchModeDescriptorsUtil
            .addProblemDescriptors(Arrays.asList(descriptors), false, this, null, toolPresentation, CONVERT));
        });

      return true;
    };
  }

  @SuppressWarnings("deprecation")
  private static void setupCancelOnWriteProgress(@NotNull Disposable disposable, @NotNull ProgressIndicator progressIndicator) {
    // avoid "attach listener"/"write action" race
    ReadAction.run(() -> {
      progressIndicator.start();
      ProgressIndicatorUtils.forceWriteActionPriority(progressIndicator, disposable);
      // there is a chance we are racing with write action, in which case just registered listener might not be called, retry.
      if (ApplicationManagerEx.getApplicationEx().isWriteActionPending()) {
        throw new ProcessCanceledException();
      }
    });
  }

  protected EnabledInspectionsProvider createEnabledInspectionsProvider(@NotNull List<? extends Tools> localTools,
                                                                        @NotNull List<? extends Tools> globalSimpleTools,
                                                                        @NotNull Project project) {
    return new EnabledInspectionsProvider() {
      @Override
      public @NotNull ToolWrappers getEnabledTools(@Nullable PsiFile psiFile, boolean includeDoNotShow) {
        return new ToolWrappers(
          localTools.stream()
            .map(tool -> tool.getEnabledTool(psiFile, includeDoNotShow))
            .filter(wrapper -> wrapper instanceof LocalInspectionToolWrapper)
            .map(wrapper -> (LocalInspectionToolWrapper)wrapper)
            .toList(),
          globalSimpleTools.stream()
            .map(tool -> tool.getEnabledTool(psiFile, includeDoNotShow))
            .filter(wrapper -> wrapper instanceof GlobalInspectionToolWrapper)
            .map(wrapper -> (GlobalInspectionToolWrapper)wrapper)
            .toList()
        );
      }
    };
  }

  protected void runExternalTools() { }

  private static @NotNull TextRange getEffectiveRange(@NotNull SearchScope searchScope, @NotNull PsiFile file) {
    if (searchScope instanceof LocalSearchScope) {
      List<PsiElement> scopeFileElements =
        ContainerUtil.filter(((LocalSearchScope)searchScope).getScope(), e -> e.getContainingFile() == file);
      if (!scopeFileElements.isEmpty()) {
        int start = -1;
        int end = -1;
        for (PsiElement scopeElement : scopeFileElements) {
          TextRange elementRange = scopeElement.getTextRange();
          start = start == -1 ? elementRange.getStartOffset() : Math.min(elementRange.getStartOffset(), start);
          end = end == -1 ? elementRange.getEndOffset() : Math.max(elementRange.getEndOffset(), end);
        }
        return new TextRange(start, end);
      }
    }
    return new TextRange(0, file.getTextLength());
  }

  // indicators which should be canceled once the main indicator myProgressIndicator is
  private final List<ProgressIndicator> dependentIndicators = ContainerUtil.createLockFreeCopyOnWriteList();

  @Override
  protected void canceled() {
    super.canceled();
    dependentIndicators.forEach(ProgressIndicator::cancel);
  }

  private void inspectFile(@NotNull PsiFile file,
                           @NotNull TextRange restrictRange,
                           @NotNull InspectionManager inspectionManager,
                           @NotNull Map<String, InspectionToolWrapper<?, ?>> wrappersMap,
                           @NotNull List<? extends GlobalInspectionToolWrapper> globalSimpleTools,
                           @NotNull List<? extends LocalInspectionToolWrapper> localTools,
                           boolean inspectInjectedPsi) {
    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
    if (document == null) return;
    DaemonProgressIndicator progressIndicator = assertUnderDaemonProgress();
    HighlightingSessionImpl.runInsideHighlightingSession(file, null, new ProperTextRange(file.getTextRange()), false, session -> {
      InspectionProfileWrapper.runWithCustomInspectionWrapper(file, __ -> new InspectionProfileWrapper(getCurrentProfile()), () -> {
        try {
          Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> map =
            InspectionEngine.inspectEx(localTools, file, restrictRange, restrictRange, false, inspectInjectedPsi, true,
                                       progressIndicator, PairProcessor.alwaysTrue());
          for (Map.Entry<LocalInspectionToolWrapper, List<ProblemDescriptor>> entry : map.entrySet()) {
            List<ProblemDescriptor> descriptors = entry.getValue();
            if (descriptors.isEmpty()) continue;
            final ProblemDescriptor firstDescriptor = descriptors.get(0);
            LocalInspectionToolWrapper toolWrapper =
              firstDescriptor instanceof ProblemDescriptorWithReporterName descriptor
              ? (LocalInspectionToolWrapper)getTools().get(descriptor.getReportingToolName()).getTool()
              : entry.getKey();
            InspectionToolPresentation toolPresentation = getPresentation(toolWrapper);
            BatchModeDescriptorsUtil.addProblemDescriptors(descriptors, toolPresentation, true, this, toolWrapper.getTool());
          }

          assertUnderDaemonProgress();

          JobLauncher.getInstance()
            .invokeConcurrentlyUnderProgress(globalSimpleTools, progressIndicator, toolWrapper -> {
              GlobalInspectionTool tool = toolWrapper.getTool();
              ProblemsHolder holder = new ProblemsHolder(inspectionManager, file, false);
              ProblemDescriptionsProcessor problemDescriptionProcessor = getProblemDescriptionProcessor(toolWrapper, wrappersMap);
              InspectionEventsKt.reportToQodanaWhenInspectionFinished(
                getInspectionEventPublisher(),
                toolWrapper,
                true,
                file,
                getProject(),
                () -> {
                  tool.checkFile(file, inspectionManager, holder, this, problemDescriptionProcessor);
                  return holder.getResultCount();
                });
              InspectionToolResultExporter toolPresentation = getPresentation(toolWrapper);
              BatchModeDescriptorsUtil.addProblemDescriptors(holder.getResults(), false, this, null, toolPresentation, CONVERT);
              return true;
            });
          VirtualFile virtualFile = file.getVirtualFile();
          String displayUrl =
            ProjectUtilCore.displayUrlRelativeToProject(virtualFile, virtualFile.getPresentableUrl(), getProject(), true, false);
          incrementJobDoneAmount(getStdJobDescriptors().LOCAL_ANALYSIS, displayUrl);
        }
        catch (ProcessCanceledException | IndexNotReadyException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error("In file: " + file.getViewProvider().getVirtualFile().getPath(), e);
        }
        finally {
          InjectedLanguageManager.getInstance(getProject()).dropFileCaches(file);
        }
      });
    });
  }

  protected boolean includeDoNotShow(InspectionProfile profile) {
    return profile.getSingleTool() != null;
  }

  private static final VirtualFile TOMBSTONE = new LightVirtualFile("TOMBSTONE");

  private @NotNull Future<?> startIterateScopeInBackground(@NotNull AnalysisScope scope,
                                                           @NotNull ProgressIndicator progressIndicator,
                                                           boolean headlessEnvironment,
                                                           @Nullable Collection<? super VirtualFile> localScopeFiles,
                                                           @NotNull BlockingQueue<? super VirtualFile> outFilesToInspect) {
    Task.Backgroundable task = new Task.Backgroundable(getProject(), InspectionsBundle.message("scanning.files.to.inspect.progress.text")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          Project project = scope.getProject();
          FileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
          scope.accept(file -> {
            ProgressManager.checkCanceled();
            if (ProjectUtil.isProjectOrWorkspaceFile(file)
                || !ReadAction.compute(() -> fileIndex.isInContent(file) || ScratchUtil.isScratch(file))) {
              return true;
            }

            PsiFile psiFile = ReadAction.compute(() -> {
              if (project.isDisposed()) throw new ProcessCanceledException();
              PsiFile psi = PsiManager.getInstance(project).findFile(file);
              Document document = psi == null ? null : shouldProcess(psi, headlessEnvironment, localScopeFiles);
              if (document != null) {
                return psi;
              }
              return null;
            });
            // do not inspect binary files
            if (psiFile != null) {
              try {
                if (ApplicationManager.getApplication().isReadAccessAllowed()) {
                  throw new IllegalStateException("Must not have read action");
                }
                outFilesToInspect.put(file);
              }
              catch (InterruptedException e) {
                LOG.error(e);
              }
            }
            ProgressManager.checkCanceled();
            return true;
          });
        }
        catch (ProcessCanceledException e) {
          // ignore, but put tombstone
        }
        finally {
          try {
            outFilesToInspect.put(TOMBSTONE);
          }
          catch (InterruptedException e) {
            LOG.error(e);
          }
        }
      }
    };
    return ((CoreProgressManager)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(task, progressIndicator, null);
  }

  private Document shouldProcess(@NotNull PsiFile file,
                                 boolean headlessEnvironment,
                                 @Nullable Collection<? super VirtualFile> localScopeFiles) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;
    if (isBinary(file)) return null; //do not inspect binary files

    if (myViewClosed && !headlessEnvironment) {
      throw new ProcessCanceledException();
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Running local inspections on " + virtualFile.getPath());
    }

    if (SingleRootFileViewProvider.isTooLargeForIntelligence(virtualFile)) return null;
    if (localScopeFiles != null && !localScopeFiles.add(virtualFile)) return null;
    if (!ProblemHighlightFilter.shouldProcessFileInBatch(file)) return null;

    return PsiDocumentManager.getInstance(getProject()).getDocument(file);
  }

  private void runGlobalTools(@NotNull AnalysisScope scope,
                              @NotNull InspectionManager inspectionManager,
                              @NotNull List<? extends Tools> globalTools,
                              boolean isOfflineInspections) {
    long timestamp = System.currentTimeMillis();
    LOG.assertTrue(!ApplicationManager.getApplication().isReadAccessAllowed() || isOfflineInspections,
                   "Must not run under read action, too unresponsive");

    if (isOfflineInspections && System.getProperty("idea.offline.no.global.inspections") != null) {
      return;
    }
    IJTracer tracer = TelemetryManager.getInstance().getTracer(GlobalInspectionScopeKt.GlobalInspectionScope);
    long refGraphTimestamp = System.currentTimeMillis();
    TraceKt.use(tracer.spanBuilder("refGraphBuilding"), __ -> {
      buildRefGraphIfNeeded(globalTools);
      return null;
    });
    long refGraphDuration = System.currentTimeMillis() - refGraphTimestamp;

    List<InspectionToolWrapper<?, ?>> needRepeatSearchRequest = new ArrayList<>();
    SearchScope initialSearchScope = ReadAction.compute(scope::toSearchScope);
    boolean canBeExternalUsages = !scope.isTotalScope();

    TraceKt.use(tracer.spanBuilder("globalInspectionsAnalysis"), __ -> {
      for (Tools tools : globalTools) {
        for (ScopeToolState state : tools.getTools()) {
          if (!state.isEnabled()) continue;
          NamedScope stateScope = state.getScope(getProject());
          if (stateScope == null) continue;

          SearchScope intersectionScope = ReadAction.compute(() ->
                                                               GlobalSearchScopesCore.filterScope(getProject(), stateScope)
                                                                 .intersectWith(initialSearchScope));
          AnalysisScope scopeForState = new AnalysisScope(intersectionScope, getProject());
          InspectionToolWrapper<?, ?> toolWrapper = state.getTool();
          GlobalInspectionTool tool = (GlobalInspectionTool)toolWrapper.getTool();
          InspectionToolResultExporter toolPresentation = getPresentation(toolWrapper);
          try {
            ThrowableRunnable<RuntimeException> runnable = () -> {
              InspectionEventsKt.reportToQodanaWhenInspectionFinished(
                getInspectionEventPublisher(),
                toolWrapper,
                false,
                null,
                getProject(),
                () -> {
                  tool.runInspection(scopeForState, inspectionManager, this, toolPresentation);
                  return toolPresentation.getProblemDescriptors().size();
                });

              //skip phase when we are sure that scope already contains everything, unused declaration though needs to proceed with its suspicious code
              if ((canBeExternalUsages || tool.getAdditionalJobs(this) != null) &&
                  tool.queryExternalUsagesRequests(inspectionManager, this, toolPresentation)) {
                needRepeatSearchRequest.add(toolWrapper);
              }
            };
            if (tool.isReadActionNeeded()) {
              ReadAction.run(runnable);
            }
            else {
              runnable.run();
            }
          }
          catch (ProcessCanceledException | IndexNotReadyException e) {
            throw e;
          }
          catch (Throwable e) {
            LOG.error(e);
          }
        }
      }
      return null;
    });
    InspectionEventsKt.reportToQodanaWhenActivityFinished(
      getInspectionEventPublisher(),
      "GLOBAL_POST_RUN_ACTIVITIES",
      getProject(),
      () -> processPostRunActivities(needRepeatSearchRequest));
    addProblemsToView(globalTools);
    InspectionPerformanceCollector.logPerformance(refGraphDuration, System.currentTimeMillis() - timestamp, scope.getFileCount(), globalTools.size());
  }

  private void buildRefGraphIfNeeded(List<? extends Tools> globalTools) {
    for (Tools tools : globalTools) {
      for (ScopeToolState state : tools.getTools()) {
        if (!state.isEnabled()) continue;
        NamedScope stateScope = state.getScope(getProject());
        if (stateScope == null) continue;

        InspectionToolWrapper<?, ?> toolWrapper = state.getTool();
        GlobalInspectionTool tool = (GlobalInspectionTool)toolWrapper.getTool();
        if (tool.isGraphNeeded()) {
          try {
            InspectionEventsKt.reportToQodanaWhenActivityFinished(
              getInspectionEventPublisher(),
              "REFERENCE_SEARCH",
              getProject(),
              () -> ((RefManagerImpl)getRefManager()).findAllDeclarations());
          }
          catch (Throwable e) {
            getStdJobDescriptors().BUILD_GRAPH.setDoneAmount(0);
            throw e;
          }
          return;
        }
      }
    }
  }

  // todo move the logic to appropriate place if it's needed
  @SuppressWarnings("unused")
  private static void logGraph(RefManager refManager) {
    List<RefElement> roots = new ArrayList<>();
    MultiMap<RefElement, RefElement> entitiesWithParents = new MultiMap<>();
    refManager.iterate(new RefVisitor() {
      @Override
      public void visitElement(@NotNull RefEntity elem) {
        if (elem instanceof RefElement refElement) {
          entitiesWithParents.put(refElement, refElement.getInReferences());
          if (refElement.getInReferences().isEmpty() || refElement.getInReferences().contains(refElement)) {
            roots.add(refElement);
          }
        }
      }
    });
    StringBuilder result = new StringBuilder("\n");
    for (RefElement root : roots) {
      List<List<RefElement>> paths = new ArrayList<>();
      paths.add(new ArrayList<>());
      traverse(root, paths, 0);
      String rootText = String.format("%s %s (owner: %s, reachable: %s, entry: %s):", root.getClass().getSimpleName(), root.getName(),
                                      root.getOwner(), root.isReachable(), root.isEntry());
      result.append(rootText).append("\n");
      for (List<RefElement> path : paths) {
        if (path.size() <= 1) continue;
        StringJoiner pathJoiner = new StringJoiner(" --> ");
        for (int i = 1; i < path.size(); i++) {
          RefElement element = path.get(i);
          String elementText = String.format("%s %s (owner: %s, reachable: %s)", element.getClass().getSimpleName(), element.getName(),
                                             element.getOwner(), element.isReachable());
          pathJoiner.add(elementText);
        }
        result.append(" --> ").append(pathJoiner).append("\n");
      }
    }
    LOG.warn(result.toString());
  }

  private static void traverse(RefElement element, List<List<RefElement>> paths, Integer depth) {
    int lastPathsIndex = paths.size() - 1;
    List<RefElement> path = paths.get(lastPathsIndex);
    if (path.contains(element)) {
      return;
    }
    path.add(element);
    List<RefElement> outRefs = new ArrayList<>(element.getOutReferences());
    for (int i = 0; i < outRefs.size(); i++) {
      RefElement outRef = outRefs.get(i);
      if (i > 0) {
        List<RefElement> pathCopy = new ArrayList<>(path.subList(0, depth + 1));
        paths.add(pathCopy);
      }
      depth++;
      traverse(outRef, paths, depth);
      depth--;
    }
  }

  private void processPostRunActivities(List<InspectionToolWrapper<?, ?>> needRepeatSearchRequest) {
    for (GlobalInspectionContextExtension<?> extension : myExtensions.values()) {
      try {
        extension.performPostRunActivities(needRepeatSearchRequest, this);
      }
      catch (ProcessCanceledException | IndexNotReadyException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  public ActionCallback initializeViewIfNeeded() {
    if (myView != null) {
      return ActionCallback.DONE;
    }
    ActionCallback callback = new ActionCallback();
    ApplicationManager.getApplication().invokeLater(() -> {
      if (getCurrentScope() == null) {
        callback.setDone();
        return;
      }
      InspectionResultsView view = getView();
      if (view == null) {
        view = new InspectionResultsView(this, new InspectionRVContentProviderImpl());
        addView(view);
      }
      callback.setDone();
    });
    return callback;
  }

  public EnabledInspectionsProvider.ToolWrappers getWrappersFromTools(
    @NotNull EnabledInspectionsProvider enabledInspectionsProvider,
    @NotNull PsiFile file,
    boolean includeDoNotShow
  ) {
    return enabledInspectionsProvider.getEnabledTools(file, includeDoNotShow);
  }

  public @NotNull <T extends @NotNull InspectionToolWrapper<?, ?>> List<T> getWrappersFromTools(
    @NotNull List<? extends Tools> localTools,
    @NotNull PsiFile file,
    boolean includeDoNotShow
  ) {
    return ContainerUtil.mapNotNull(localTools, tool -> {
      @SuppressWarnings({"unchecked", "DataFlowIssue"}) T enabledTool = (T)tool.getEnabledTool(file, includeDoNotShow);
      return enabledTool;
    });
  }

  private @NotNull ProblemDescriptionsProcessor getProblemDescriptionProcessor(
    @NotNull GlobalInspectionToolWrapper toolWrapper,
    @NotNull Map<String, InspectionToolWrapper<?, ?>> wrappersMap
  ) {
    return new ProblemDescriptionsProcessor() {
      @Override
      public void addProblemElement(@Nullable RefEntity refEntity, CommonProblemDescriptor @NotNull ... commonProblemDescriptors) {
        for (CommonProblemDescriptor problemDescriptor : commonProblemDescriptors) {
          if (!(problemDescriptor instanceof ProblemDescriptor)) {
            continue;
          }
          if (SuppressionUtil.inspectionResultSuppressed(((ProblemDescriptor)problemDescriptor).getPsiElement(), toolWrapper.getTool())) {
            continue;
          }
          ProblemGroup problemGroup = ((ProblemDescriptor)problemDescriptor).getProblemGroup();

          InspectionToolWrapper<?, ?> targetWrapper = problemGroup == null ? toolWrapper : wrappersMap.get(problemGroup.getProblemName());
          if (targetWrapper != null) { // Else it's switched off
            InspectionToolResultExporter toolPresentation = getPresentation(targetWrapper);
            toolPresentation.addProblemElement(refEntity, problemDescriptor);
          }
        }
      }
    };
  }

  private static @NotNull Map<String, InspectionToolWrapper<?, ?>> getInspectionWrappersMap(@NotNull List<? extends Tools> tools) {
    Map<String, InspectionToolWrapper<?, ?>> name2Inspection = new HashMap<>(tools.size());
    for (Tools tool : tools) {
      InspectionToolWrapper<?, ?> toolWrapper = tool.getTool();
      name2Inspection.put(toolWrapper.getShortName(), toolWrapper);
    }

    return name2Inspection;
  }

  private static final TripleFunction<LocalInspectionTool, PsiElement, GlobalInspectionContext, RefElement> CONVERT =
    (tool, elt, context) -> {
      PsiNamedElement problemElement = PsiTreeUtil.getNonStrictParentOfType(elt, PsiFile.class);

      RefElement refElement = context.getRefManager().getReference(problemElement);
      if (refElement == null && problemElement != null) {  // no need to lose collected results
        refElement = GlobalInspectionContextUtil.retrieveRefElement(elt, context);
      }
      return refElement;
    };


  @Override
  public void close(boolean noSuspiciousCodeFound) {
    if (!noSuspiciousCodeFound) {
      if (myView.isRerun()) {
        myViewClosed = true;
        myView = null;
      }
      if (myView == null) {
        return;
      }
    }
    if (myContent != null) {
      ContentManager contentManager = getContentManager();
      contentManager.removeContent(myContent, true);
    }
    myViewClosed = true;
    myView = null;
    ((InspectionManagerEx)InspectionManager.getInstance(getProject())).closeRunningContext(this);
    myPresentationMap.clear();
    super.close(noSuspiciousCodeFound);
  }

  @Override
  public void cleanup() {
    if (myView != null) {
      myView.setUpdating(false);
    }
    else {
      myPresentationMap.clear();
      super.cleanup();
    }
  }

  public void refreshViews() {
    if (myView != null) {
      myView.getTree().getInspectionTreeModel().reload();
    }
  }

  private @Nullable InspectionToolResultExporter getPresentationOrNull(@NotNull InspectionToolWrapper<?, ?> toolWrapper) {
    return myPresentationMap.get(toolWrapper);
  }

  @Override
  @SuppressWarnings("LoggingSimilarMessage")
  public void codeCleanup(
    @NotNull AnalysisScope scope,
    @NotNull InspectionProfile profile,
    @Nullable String commandName,
    @Nullable Runnable postRunnable,
    boolean modal,
    @NotNull Predicate<? super ProblemDescriptor> shouldApplyFix
  ) {
    @SuppressWarnings("DialogTitleCapitalization") String title = LangBundle.message("progress.title.inspect.code", profile.getName());
    if (LOG.isDebugEnabled()) {
      LOG.debug("Starting code cleanup");
    }
    Runnable retryRunnable = () -> codeCleanup(scope, profile, commandName, postRunnable, modal, shouldApplyFix);

    class TaskDelegate {
      CleanupProblems problems;
      @NlsContexts.NotificationContent String noProblemsMessage;

      void run(@NotNull ProgressIndicator indicator) {
        problems = findProblems(scope, profile, indicator, shouldApplyFix);
        if (problems.files().isEmpty()) {
          noProblemsMessage =
            commandName == null ? null :
            InspectionsBundle.message("inspection.no.problems.message", scope.getFileCount(), scope.getDisplayName());
        }
      }

      void onSuccess() {
        boolean isFinished;
        if (problems.files().isEmpty()) {
          isFinished = reportNoProblemsFound(scope, noProblemsMessage, retryRunnable);
        }
        else {
          isFinished = applyFixes(problems);
        }
        if (isFinished && postRunnable != null) {
          postRunnable.run();
        }
      }
    }

    TaskDelegate delegate = new TaskDelegate();
    Task task = modal ? new Task.Modal(getProject(), title, true) {

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        delegate.run(indicator);
      }

      @Override
      public void onSuccess() {
        delegate.onSuccess();
      }

      @Override
      public void onThrowable(@NotNull Throwable error) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Code cleanup: an exception during findProblems");
        }
        super.onThrowable(error);
      }

      @Override
      public void onCancel() {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Code cleanup: cancelled");
        }
      }

      @Override
      public void onFinished() {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Code cleanup: finished searching for problems");
        }
      }
    } : new Task.Backgroundable(getProject(), title, true) {

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        delegate.run(indicator);
      }

      @Override
      public void onSuccess() {
        delegate.onSuccess();
      }

      @Override
      public void onThrowable(@NotNull Throwable error) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Code cleanup: an exception during findProblems");
        }
        super.onThrowable(error);
      }

      @Override
      public void onCancel() {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Code cleanup: cancelled");
        }
      }

      @Override
      public void onFinished() {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Code cleanup: finished searching for problems");
        }
      }
    };
    ProgressManager.getInstance().run(task);
  }

  public @NotNull CleanupProblems findProblems(@NotNull AnalysisScope scope,
                                               @NotNull InspectionProfile profile,
                                               @NotNull ProgressIndicator progressIndicator,
                                               @NotNull Predicate<? super ProblemDescriptor> shouldApplyFix) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Code cleanup: searching for problems in code");
    }

    setCurrentScope(scope);
    int fileCount = scope.getFileCount();
    progressIndicator.setIndeterminate(false);
    SearchScope searchScope = ReadAction.compute(scope::toSearchScope);
    TextRange range;
    if (searchScope instanceof LocalSearchScope) {
      PsiElement[] elements = ((LocalSearchScope)searchScope).getScope();
      range = elements.length == 1 ? ReadAction.compute(elements[0]::getTextRange) : null;
    }
    else {
      range = null;
    }
    Iterable<Tools> inspectionTools = ContainerUtil.filter(profile.getAllEnabledInspectionTools(getProject()), tools -> {
      assert tools != null;
      return tools.getTool().isCleanupTool();
    });
    boolean includeDoNotShow = includeDoNotShow(profile);
    List<ProblemDescriptor> descriptors = new ArrayList<>();
    Set<PsiFile> files = new HashSet<>();
    ((RefManagerImpl)getRefManager()).runInsideInspectionReadAction(() ->
      scope.accept(new PsiElementVisitor() {
        private int myCount;

        @Override
        public void visitFile(@NotNull PsiFile file) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Code cleanup: searching for problems in file " + file);
          }

          progressIndicator.setText(AbstractLayoutCodeProcessor.getPresentablePath(getProject(), file));
          progressIndicator.setFraction((double)++myCount / fileCount);

          if (isBinary(file)) return;
          List<LocalInspectionToolWrapper> lTools = new ArrayList<>();
          for (Tools tools : inspectionTools) {
            InspectionToolWrapper<?, ?> tool = tools.getEnabledTool(file, includeDoNotShow);
            if (tool != null && profile instanceof InspectionProfileImpl profileImpl) {
              @NotNull Set<InspectionToolWrapper<?, ?>> other = new HashSet<>();
              profileImpl.collectDependentInspections(tool, other, getProject());
              for (InspectionToolWrapper<?, ?> wrapper : other) {
                if (wrapper instanceof LocalInspectionToolWrapper) {
                  lTools.add((LocalInspectionToolWrapper)wrapper);
                  wrapper.initialize(GlobalInspectionContextImpl.this);
                }
              }
            }
            if (tool instanceof GlobalInspectionToolWrapper) {
              tool = ((GlobalInspectionToolWrapper)tool).getSharedLocalInspectionToolWrapper();
            }
            if (tool != null) {
              lTools.add((LocalInspectionToolWrapper)tool);
              tool.initialize(GlobalInspectionContextImpl.this);
            }
          }

          if (!lTools.isEmpty()) {
            InspectionProfileWrapper.runWithCustomInspectionWrapper(file,
                p -> new InspectionProfileWrapper(profile, ((InspectionProfileImpl)p).getProfileManager()),
                () -> findProblemsInFile(file, lTools, range, searchScope, descriptors, files, shouldApplyFix));
          }
        }
      }));

    return new CleanupProblems(files, descriptors, searchScope instanceof GlobalSearchScope);
  }

  private void findProblemsInFile(@NotNull PsiFile file,
                                  @NotNull List<? extends LocalInspectionToolWrapper> localTools,
                                  @Nullable TextRange range,
                                  @NotNull SearchScope searchScope,
                                  @NotNull List<? super ProblemDescriptor> descriptorResults,
                                  @NotNull Set<? super PsiFile> visitedFiles,
                                  @NotNull Predicate<? super ProblemDescriptor> shouldApplyFix) {
    try {
      TextRange restrictRange =
        range == null ? file.getTextRange() : range;
      ApplicationManager.getApplication().runReadAction(() -> {
        Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> map =
          InspectionEngine.inspectEx(localTools, file, restrictRange,
                                     restrictRange, false, true, true,
                                     myProgressIndicator,
                                     (__, ___) -> true);
        for (Map.Entry<LocalInspectionToolWrapper, List<ProblemDescriptor>> entry : map.entrySet()) {
          LocalInspectionToolWrapper toolWrapper = entry.getKey();
          List<ProblemDescriptor> descriptors = entry.getValue();
          InspectionToolPresentation toolPresentation =
            getPresentation(toolWrapper);
          BatchModeDescriptorsUtil.addProblemDescriptors(descriptors,
                                                         toolPresentation,
                                                         true,
                                                         this,
                                                         toolWrapper.getTool());
        }
      });

      Set<ProblemDescriptor> localDescriptors =
        new TreeSet<>(CommonProblemDescriptor.DESCRIPTOR_COMPARATOR);
      for (LocalInspectionToolWrapper tool : localTools) {
        InspectionToolResultExporter toolPresentation =
          getPresentation(tool);
        for (CommonProblemDescriptor descriptor : toolPresentation.getProblemDescriptors()) {
          if (descriptor instanceof ProblemDescriptor) {
            localDescriptors.add((ProblemDescriptor)descriptor);
          }
        }
      }

      if (searchScope instanceof LocalSearchScope) {
        for (Iterator<ProblemDescriptor> iterator =
             localDescriptors.iterator(); iterator.hasNext(); ) {
          ProblemDescriptor descriptor = iterator.next();
          TextRange infoRange =
            descriptor instanceof ProblemDescriptorBase
            ? ((ProblemDescriptorBase)descriptor).getTextRange()
            : null;
          if (infoRange != null &&
              !((LocalSearchScope)searchScope).containsRange(file,
                                                             infoRange)) {
            iterator.remove();
          }
        }
      }
      if (!localDescriptors.isEmpty()) {
        for (ProblemDescriptor descriptor : localDescriptors) {
          if (shouldApplyFix.test(descriptor)) {
            descriptorResults.add(descriptor);
          }
        }
        visitedFiles.add(file);
      }
    }
    finally {
      myPresentationMap.clear();
    }
  }

  private boolean reportNoProblemsFound(@NotNull AnalysisScope scope,
                                        @Nullable @NlsContexts.NotificationContent String message,
                                        @NotNull Runnable retryRunnable) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("No problems found during code inspection, nothing to cleanup");
    }
    if (message != null) {
      var notification = new Notification(
        NOTIFICATION_GROUP,
        message,
        NotificationType.INFORMATION);
      if (!scope.isIncludeTestSource()) {
        addRepeatWithTestsAction(scope, notification, retryRunnable);
      }
      notification.notify(getProject());
    }
    return true;
  }

  private boolean applyFixes(@NotNull CleanupProblems problems) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Applying fixes");
    }

    if (!FileModificationService.getInstance().preparePsiElementsForWrite(problems.files())) return false;
    CleanupInspectionUtil.getInstance().applyFixesNoSort(
      getProject(), LangBundle.message("code.cleanup"), problems.problemDescriptors(), null, false, problems.isGlobalScope());
    return true;
  }

  @SuppressWarnings("IdentifierGrammar")
  private static void addRepeatWithTestsAction(
    @NotNull AnalysisScope scope,
    @NotNull Notification notification,
    @NotNull Runnable analysisRepeater
  ) {
    notification.addAction(new NotificationAction(InspectionsBundle.message("inspection.no.problems.repeat.with.tests")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        notification.expire();
        scope.invalidate();
        scope.setIncludeTestSource(true);
        analysisRepeater.run();
      }
    });
  }

  private static boolean isBinary(@NotNull PsiFile file) {
    return file instanceof PsiBinaryFile || file.getFileType().isBinary();
  }

  public boolean isViewClosed() {
    return myViewClosed;
  }

  private void addProblemsToView(List<? extends Tools> tools) {
    //noinspection TestOnlyProblems
    if (ApplicationManager.getApplication().isHeadlessEnvironment() && !TESTING_VIEW) {
      return;
    }
    if (myView == null && !InspectionResultsView.hasProblems(tools, this, new InspectionRVContentProviderImpl())) {
      return;
    }
    initializeViewIfNeeded().doWhenDone(() -> myView.addTools(tools));
  }

  @Override
  public @NotNull InspectionToolPresentation getPresentation(@NotNull InspectionToolWrapper<?, ?> toolWrapper) {
    return myPresentationMap.computeIfAbsent(toolWrapper, __ -> {
      String presentationClass = toolWrapper.myEP == null ? null : toolWrapper.myEP.presentation;
      if (StringUtil.isEmpty(presentationClass)) {
        if (myProblemConsumer != null) {
          return new DelegatedInspectionToolPresentation(toolWrapper, this, myProblemConsumer);
        }
        presentationClass = DefaultInspectionToolPresentation.class.getName();
      }
      try {
        InspectionEP extension = toolWrapper.getExtension();
        ClassLoader extensionClassLoader = extension != null ? extension.getPluginDescriptor().getPluginClassLoader() : null;
        Constructor<?> constructor = Class.forName(presentationClass,
                                                   true,
                                                   extensionClassLoader != null ? extensionClassLoader : getClass().getClassLoader())
          .getConstructor(InspectionToolWrapper.class, GlobalInspectionContextImpl.class);
        constructor.setAccessible(true);
        return (InspectionToolPresentation)constructor.newInstance(toolWrapper, this);
      }
      catch (Exception e) {
        LOG.error(e);
        throw new RuntimeException(e);
      }
    });
  }

  static final class InspectionPerformanceCollector extends CounterUsagesCollector {
    private static final EventLogGroup GROUP = new EventLogGroup("inspection.performance", 3);

    static final LongEventField TOTAL_DURATION = new LongEventField("total_duration_ms");
    static final LongEventField BUILD_REFERENCE_GRAPH_DURATION = new LongEventField("build_reference_graph_duration_ms");
    static final RoundedIntEventField NUMBER_OF_FILES = new RoundedIntEventField("number_of_files");
    static final IntEventField NUMBER_OF_INSPECTIONS = new IntEventField("number_of_inspections");

    static final VarargEventId GLOBAL_INSPECTION_FINISHED = GROUP.registerVarargEvent("global.inspection.finished",
                                                                                      TOTAL_DURATION,
                                                                                      BUILD_REFERENCE_GRAPH_DURATION,
                                                                                      NUMBER_OF_FILES,
                                                                                      NUMBER_OF_INSPECTIONS);

    @Override
    public EventLogGroup getGroup() {
      return GROUP;
    }

    static void logPerformance(long refGraphDuration, long globalInspectionsDuration, int fileCount, int inspectionCount) {
      GLOBAL_INSPECTION_FINISHED.log(TOTAL_DURATION.with(globalInspectionsDuration),
                                     BUILD_REFERENCE_GRAPH_DURATION.with(refGraphDuration),
                                     NUMBER_OF_FILES.with(fileCount),
                                     NUMBER_OF_INSPECTIONS.with(inspectionCount));
    }
  }
}
