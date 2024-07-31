// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.analysis.problemsView.toolWindow.ProblemsView;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.icons.AllIcons;
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.impl.ProjectUtilKt;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SlowOperations;
import com.intellij.util.UtilBundle;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

public class TrafficLightRenderer implements ErrorStripeRenderer, Disposable {
  private final @NotNull Project myProject;
  private final @NotNull Document myDocument;
  private final DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;
  private final SeverityRegistrar mySeverityRegistrar;
  private final Object2IntMap<HighlightSeverity> errorCount = Object2IntMaps.synchronize(new Object2IntOpenHashMap<>());
  private final @NotNull UIController myUIController;
  private final boolean inLibrary; // true if getPsiFile() is in library sources
  private final boolean shouldHighlight;
  private int[] cachedErrors = ArrayUtilRt.EMPTY_INT_ARRAY;
  private final Map<Language, FileHighlightingSetting> myFileHighlightingSettings; // each root language -> its highlighting level
  private volatile long myHighlightingSettingsModificationCount;

  /**
   * Prefer using {@link TrafficLightRendererContributor} instead
   */
  public static void setTrafficLightOnEditor(@NotNull Project project,
                                             @NotNull EditorMarkupModel editorMarkupModel,
                                             @NotNull ModalityState modalityState,
                                             @NotNull Supplier<? extends @Nullable TrafficLightRenderer> createTrafficRenderer) {
    ProjectUtilKt.executeOnPooledThread(project, () -> {
      TrafficLightRenderer tlRenderer = createTrafficRenderer.get();
      if (tlRenderer == null) return;

      ApplicationManager.getApplication().invokeLater(() -> {
        Editor editor = editorMarkupModel.getEditor();
        if (project.isDisposed() || editor.isDisposed()) {
          Disposer.dispose(tlRenderer); // would be registered in setErrorStripeRenderer() below
          return;
        }
        editorMarkupModel.setErrorStripeRenderer(tlRenderer);
      }, modalityState);
    });
  }

  public TrafficLightRenderer(@NotNull Project project, @NotNull Document document) {
    this(project, document, null);
  }
  protected TrafficLightRenderer(@NotNull Project project, @NotNull Editor editor) {
    this(project, editor.getDocument(), editor);
  }
  private TrafficLightRenderer(@NotNull Project project, @NotNull Document document, @Nullable Editor editor) {
    ApplicationManager.getApplication().assertIsNonDispatchThread(); // to be able to find PsiFile without "slow op in EDT" exceptions
    myProject = project;
    myDaemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
    myDocument = document;
    mySeverityRegistrar = SeverityRegistrar.getSeverityRegistrar(myProject);

    init(project, myDocument);
    myUIController = editor == null ? createUIController() : createUIController(editor);
    record Stuff(@NotNull Map<Language, FileHighlightingSetting> fileHighlightingSettings,
                 boolean inLibrary,
                 boolean shouldHighlight){}
    Stuff info = ReadAction.compute(() -> {
      PsiFile psiFile = getPsiFile();
      if (psiFile == null) {
        return new Stuff(Collections.emptyMap(),false,false);
      }
      FileViewProvider viewProvider = psiFile.getViewProvider();
      Set<Language> languages = viewProvider.getLanguages();
      Map<Language, FileHighlightingSetting> settingMap = new HashMap<>(languages.size());
      HighlightingSettingsPerFile settings = HighlightingSettingsPerFile.getInstance(project);
      for (PsiFile psiRoot : viewProvider.getAllFiles()) {
        FileHighlightingSetting setting = settings.getHighlightingSettingForRoot(psiRoot);
        settingMap.put(psiRoot.getLanguage(), setting);
      }

      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      VirtualFile virtualFile = psiFile.getVirtualFile();
      assert virtualFile != null;
      boolean inLib = fileIndex.isInLibrary(virtualFile) && !fileIndex.isInContent(virtualFile);
      boolean shouldHighlight = ProblemHighlightFilter.shouldHighlightFile(getPsiFile());
      return new Stuff(settingMap, inLib, shouldHighlight);
    });
    myFileHighlightingSettings = info.fileHighlightingSettings();
    inLibrary = info.inLibrary();
    shouldHighlight = info.shouldHighlight();
    myHighlightingSettingsModificationCount = HighlightingSettingsPerFile.getInstance(project).getModificationCount();
  }

  private void init(@NotNull Project project, @NotNull Document document) {
    refresh(null);

    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(document, project, true);
    model.addMarkupModelListener(this, new MarkupModelListener() {
      @Override
      public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
        incErrorCount(highlighter, 1);
      }

      @Override
      public void afterRemoved(@NotNull RangeHighlighterEx highlighter) {
        incErrorCount(highlighter, -1);
      }
    });
    UIUtil.invokeLaterIfNeeded(() -> {
      for (RangeHighlighter rangeHighlighter : model.getAllHighlighters()) {
        incErrorCount(rangeHighlighter, 1);
      }
    });
  }

  private PsiFile getPsiFile() {
    return PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
  }

  public @NotNull SeverityRegistrar getSeverityRegistrar() {
    return mySeverityRegistrar;
  }

  /**
   * Returns a new instance of an array filled with a number of highlighters with a given severity.
   * {@code errorCount[idx]} equals to a number of highlighters of severity with index {@code idx} in this markup model.
   * Severity index can be obtained via {@link SeverityRegistrar#getSeverityIdx(HighlightSeverity)}.
   */
  public int @NotNull [] getErrorCounts() {
    return cachedErrors.clone();
  }

  protected void refresh(@Nullable EditorMarkupModelImpl editorMarkupModel) {
    List<HighlightSeverity> severities = mySeverityRegistrar.getAllSeverities();
    if (cachedErrors.length != severities.size()) {
      cachedErrors = new int[severities.size()];
    }

    for (HighlightSeverity severity : severities) {
      int severityIndex = mySeverityRegistrar.getSeverityIdx(severity);
      cachedErrors[severityIndex] = errorCount.getInt(severity);
    }
  }

  @Override
  public void dispose() {
    errorCount.clear();
    cachedErrors = ArrayUtilRt.EMPTY_INT_ARRAY;
  }

  private void incErrorCount(@NotNull RangeHighlighter highlighter, int delta) {
    HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
    if (info == null) return;
    HighlightSeverity infoSeverity = info.getSeverity();
    if (infoSeverity.myVal <= HighlightSeverity.TEXT_ATTRIBUTES.myVal) return;

    errorCount.mergeInt(infoSeverity, delta, Integer::sum);
  }

  /**
   * when highlighting level changed, re-create TrafficLightRenderer (and recompute levels in its ctr)
   * @see ErrorStripeUpdateManager#setOrRefreshErrorStripeRenderer(EditorMarkupModel, PsiFile)
   */
  public boolean isValid() {
    PsiFile psiFile;
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-301732, EA-829415")) {
      psiFile = getPsiFile();
      if (psiFile == null) return false;
    }
    HighlightingSettingsPerFile settings = HighlightingSettingsPerFile.getInstance(psiFile.getProject());
    return settings.getModificationCount() == myHighlightingSettingsModificationCount;
  }

  @ApiStatus.Internal
  public static final class DaemonCodeAnalyzerStatus {
    public boolean errorAnalyzingFinished;  // all passes are done
    List<ProgressableTextEditorHighlightingPass> passes = Collections.emptyList();
    public int[] errorCounts = ArrayUtilRt.EMPTY_INT_ARRAY;
    public @Nls String reasonWhyDisabled;
    public @Nls String reasonWhySuspended;

    public HeavyProcessLatch.Type heavyProcessType;
    private FileHighlightingSetting minimumLevel = FileHighlightingSetting.FORCE_HIGHLIGHTING;  // by default, full inspect mode is expected

    DaemonCodeAnalyzerStatus() {
    }

    @Override
    public String toString() {
      StringBuilder s = new StringBuilder("DS: finished=" + errorAnalyzingFinished
                                          + "; pass statuses: " + passes.size() + "; ");
      for (ProgressableTextEditorHighlightingPass passStatus : passes) {
        s.append(
          String.format("(%s %2.0f%% %b)", passStatus.getPresentableName(), passStatus.getProgress() * 100, passStatus.isFinished()));
      }
      s.append("; error counts: ").append(errorCounts.length).append(": ").append(new IntArrayList(errorCounts));
      if (reasonWhyDisabled != null) {
        s.append("; reasonWhyDisabled=").append(reasonWhyDisabled);
      }
      if (reasonWhySuspended != null) {
        s.append("; reasonWhySuspended").append(reasonWhySuspended);
      }
      return s.toString();
    }
  }

  @ApiStatus.Internal
  public @NotNull DaemonCodeAnalyzerStatus getDaemonCodeAnalyzerStatus() {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return getDaemonCodeAnalyzerStatus(mySeverityRegistrar);
  }

  protected @NotNull DaemonCodeAnalyzerStatus getDaemonCodeAnalyzerStatus(@NotNull SeverityRegistrar severityRegistrar) {
    // this method is rather expensive and PSI-related, need to execute in BGT and cache the result to show in EDT later
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    DaemonCodeAnalyzerStatus status = new DaemonCodeAnalyzerStatus();
    status.errorAnalyzingFinished = true;
    PsiFile psiFile = getPsiFile();
    if (psiFile == null) {
      status.reasonWhyDisabled = DaemonBundle.message("process.title.no.file");
      return status;
    }
    if (myProject.isDisposed()) {
      status.reasonWhyDisabled = DaemonBundle.message("process.title.project.is.disposed");
      return status;
    }
    if (!myDaemonCodeAnalyzer.isHighlightingAvailable(psiFile)) {
      if (!psiFile.isPhysical()) {
        status.reasonWhyDisabled = DaemonBundle.message("process.title.file.is.generated");
        return status;
      }
      if (psiFile instanceof PsiCompiledElement) {
        status.reasonWhyDisabled = DaemonBundle.message("process.title.file.is.decompiled");
        return status;
      }
      FileType fileType = psiFile.getFileType();
      if (fileType.isBinary()) {
        status.reasonWhyDisabled = DaemonBundle.message("process.title.file.is.binary");
        return status;
      }
      status.reasonWhyDisabled = DaemonBundle.message("process.title.highlighting.is.disabled.for.this.file");
      return status;
    }

    FileViewProvider provider = psiFile.getViewProvider();
    Set<Language> languages = provider.getLanguages();
    boolean shouldHighlight = languages.isEmpty();

    for (Map.Entry<Language, FileHighlightingSetting> entry : myFileHighlightingSettings.entrySet()) {
      FileHighlightingSetting level = entry.getValue();
      shouldHighlight |= level != FileHighlightingSetting.SKIP_HIGHLIGHTING;
      status.minimumLevel = status.minimumLevel.compareTo(level) < 0 ? status.minimumLevel : level;
    }
    shouldHighlight &= this.shouldHighlight;

    if (!shouldHighlight) {
      status.reasonWhyDisabled = DaemonBundle.message("process.title.highlighting.level.is.none");
      return status;
    }

    HeavyProcessLatch.Operation heavyOperation = HeavyProcessLatch.INSTANCE.findRunningExcept(HeavyProcessLatch.Type.Syncing);
    if (heavyOperation != null) {
      status.reasonWhySuspended = heavyOperation.getDisplayName();
      status.heavyProcessType = heavyOperation.getType();
      return status;
    }

    status.errorCounts = getErrorCounts();
    status.passes = ContainerUtil.filter(myDaemonCodeAnalyzer.getPassesToShowProgressFor(myDocument),
                                         p -> !StringUtil.isEmpty(p.getPresentableName()) && p.getProgress() >= 0);

    status.errorAnalyzingFinished = myDaemonCodeAnalyzer.isAllAnalysisFinished(psiFile);
    if (!myDaemonCodeAnalyzer.isUpdateByTimerEnabled()) {
      status.reasonWhySuspended = DaemonBundle.message("process.title.highlighting.is.paused.temporarily");
    }
    fillDaemonCodeAnalyzerErrorsStatus(status, severityRegistrar);

    return status;
  }

  protected void fillDaemonCodeAnalyzerErrorsStatus(@NotNull DaemonCodeAnalyzerStatus status, @NotNull SeverityRegistrar severityRegistrar) {
  }

  protected final @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull AnalyzerStatus getStatus() {
    // this method is rather expensive and PSI-related, need to execute in BGT and cache the result to show in EDT later
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (PowerSaveMode.isEnabled()) {
      return new AnalyzerStatus(AllIcons.General.InspectionsPowerSaveMode,
                                InspectionsBundle.message("code.analysis.is.disabled.in.power.save.mode"),
                                "",
                                myUIController).withState(InspectionsState.DISABLED);
    }
    DaemonCodeAnalyzerStatus status = getDaemonCodeAnalyzerStatus(mySeverityRegistrar);

    String title;
    String details;
    InspectionsState state;
    boolean isDumb = DumbService.isDumb(myProject);

    List<SeverityStatusItem> statusItems = new ArrayList<>();
    int[] errorCounts = status.errorCounts;
    for (int i = errorCounts.length - 1; i >= 0; i--) {
      int count = errorCounts[i];
      if (count > 0) {
        HighlightSeverity severity = mySeverityRegistrar.getSeverityByIndex(i);
        if (severity != null) {
          Icon icon = mySeverityRegistrar.getRendererIconBySeverity(severity, status.minimumLevel == FileHighlightingSetting.FORCE_HIGHLIGHTING);
          SeverityStatusItem next = new SeverityStatusItem(severity, icon, count, severity.getCountMessage(count));
          while (!statusItems.isEmpty()) {
            SeverityStatusItem merged = StatusItemMerger.runMerge(ContainerUtil.getLastItem(statusItems), next);
            if (merged == null) break;

            statusItems.remove(statusItems.size() - 1);
            next = merged;
          }
          statusItems.add(next);
        }
      }
    }

    if (status.errorAnalyzingFinished) {
      if (isDumb) {
        title = DaemonBundle.message("shallow.analysis.completed");
        details = DaemonBundle.message("shallow.analysis.completed.details");
        state = InspectionsState.SHALLOW_ANALYSIS_COMPLETE;
      }
      else if (myFileHighlightingSettings.containsValue(FileHighlightingSetting.ESSENTIAL)) {
        title = DaemonBundle.message("essential.analysis.completed");
        details = DaemonBundle.message("essential.analysis.completed.details");
        state = InspectionsState.ESSENTIAL_ANALYSIS_COMPLETE;
      }
      else {
        title = statusItems.isEmpty() ? DaemonBundle.message("no.errors.or.warnings.found") : "";
        details = "";
        state = InspectionsState.NO_PROBLEMS_FOUND;
      }
    }
    else {
      title = DaemonBundle.message("performing.code.analysis");
      details = "";
      state = InspectionsState.PERFORMING_CODE_ANALYSIS;
    }

    if (!statusItems.isEmpty()) {
      AnalyzerStatus result = new AnalyzerStatus(statusItems.get(0).getIcon(), title, "", myUIController).
        withNavigation(true).
        withState(state).
        withExpandedStatus(ContainerUtil.map(statusItems, i -> {
          TrafficLightStatusItemMetadata metadata = new TrafficLightStatusItemMetadata(i.getProblemCount(), i.getSeverity());
          return new StatusItem(Integer.toString(i.getProblemCount()), i.getIcon(), i.getCountMessage(), metadata);
        }));

      return status.errorAnalyzingFinished ? result :
             result.withAnalyzingType(AnalyzingType.PARTIAL).
             withPasses(ContainerUtil.map(status.passes, pass -> new PassWrapper(pass.getPresentableName(), toPercent(pass.getProgress(), pass.isFinished()))));
    }
    if (StringUtil.isNotEmpty(status.reasonWhyDisabled)) {
      return new AnalyzerStatus(AllIcons.General.InspectionsTrafficOff,
                                DaemonBundle.message("no.analysis.performed"),
                                status.reasonWhyDisabled, myUIController).withTextStatus(DaemonBundle.message("iw.status.off")).withState(InspectionsState.OFF);
    }
    if (StringUtil.isNotEmpty(status.reasonWhySuspended)) {
      return new AnalyzerStatus(AllIcons.General.InspectionsPause,
                                DaemonBundle.message("analysis.suspended"),
                                status.reasonWhySuspended, myUIController).
        withState(InspectionsState.PAUSED).
        withTextStatus(status.heavyProcessType != null ? status.heavyProcessType.toString() : DaemonBundle.message("iw.status.paused")).
        withAnalyzingType(AnalyzingType.SUSPENDED);
    }
    if (status.errorAnalyzingFinished) {
      Icon inspectionsCompletedIcon = status.minimumLevel == FileHighlightingSetting.FORCE_HIGHLIGHTING
                                      ? AllIcons.General.InspectionsOK
                                      : AllIcons.General.InspectionsOKEmpty;
      return isDumb ?
        new AnalyzerStatus(AllIcons.General.InspectionsPause, title, details, myUIController).
          withTextStatus(UtilBundle.message("heavyProcess.type.indexing")).
          withState(InspectionsState.INDEXING).
          withAnalyzingType(AnalyzingType.SUSPENDED) :
        new AnalyzerStatus(inspectionsCompletedIcon, title, details, myUIController);
    }

    return new AnalyzerStatus(AllIcons.General.InspectionsEye, DaemonBundle.message("no.errors.or.warnings.found"), details, myUIController).
      withTextStatus(DaemonBundle.message("iw.status.analyzing")).
      withState(InspectionsState.ANALYZING).
      withAnalyzingType(AnalyzingType.EMPTY).
      withPasses(ContainerUtil.map(status.passes, pass -> new PassWrapper(pass.getPresentableName(), toPercent(pass.getProgress(), pass.isFinished()))));
  }

  private static int toPercent(double progress, boolean finished) {
    int percent = (int)(progress * 100);
    return percent == 100 && !finished ? 99 : percent;
  }


  protected @NotNull UIController createUIController() {
    ApplicationManager.getApplication().assertIsNonDispatchThread(); // to assert no slow ops in EDT
    return new AbstractUIController();
  }

  protected final @NotNull UIController createUIController(@NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsNonDispatchThread(); // to assert no slow ops in EDT
    boolean mergeEditor = editor.getUserData(DiffUserDataKeys.MERGE_EDITOR_FLAG) == Boolean.TRUE;
    return editor.getEditorKind() == EditorKind.DIFF && !mergeEditor ? new AbstractUIController() : new DefaultUIController();
  }

  protected class AbstractUIController implements UIController {
    private @NotNull List<HectorComponentPanel> myAdditionalPanels = Collections.emptyList();

    AbstractUIController() {
      ApplicationManager.getApplication().assertIsNonDispatchThread();
    }
    @Override
    public @NotNull List<InspectionsLevel> getAvailableLevels() {
      return inLibrary ?
               Arrays.asList(InspectionsLevel.NONE, InspectionsLevel.SYNTAX) :
             ApplicationManager.getApplication().isInternal() ?
               Arrays.asList(InspectionsLevel.NONE, InspectionsLevel.SYNTAX, InspectionsLevel.ESSENTIAL, InspectionsLevel.ALL) :
               Arrays.asList(InspectionsLevel.NONE, InspectionsLevel.SYNTAX, InspectionsLevel.ALL);
    }

    @Override
    public @NotNull List<LanguageHighlightLevel> getHighlightLevels() {
      return ContainerUtil.map(myFileHighlightingSettings.entrySet(),
                               entry -> new LanguageHighlightLevel(entry.getKey().getID(), FileHighlightingSetting.toInspectionsLevel(entry.getValue())));
    }

    @Override
    public void setHighLightLevel(@NotNull LanguageHighlightLevel level) {
      PsiFile psiFile = getPsiFile();
      if (psiFile != null && !getProject().isDisposed() && !getHighlightLevels().contains(level)) {
        FileViewProvider viewProvider = psiFile.getViewProvider();

        Language language = Language.findLanguageByID(level.getLangID());
        if (language != null) {
          PsiElement root = viewProvider.getPsi(language);
          if (root == null) return;
          FileHighlightingSetting setting = FileHighlightingSetting.fromInspectionsLevel(level.getLevel());
          HighlightLevelUtil.forceRootHighlighting(root, setting);
          InjectedLanguageManager.getInstance(getProject()).dropFileCaches(psiFile);
          myDaemonCodeAnalyzer.restart();
          // after that TrafficLightRenderer will be recreated anew, no need to patch myFileHighlightingSettings
        }
      }
    }

    @Override
    public void fillHectorPanels(@NotNull Container container, @NotNull GridBag gc) {
      PsiFile psiFile = getPsiFile();
      if (psiFile != null) {
        List<HectorComponentPanel> list = new ArrayList<>();
        for (HectorComponentPanelsProvider hp : HectorComponentPanelsProvider.EP_NAME.getExtensionList(getProject())) {
          HectorComponentPanel configurable = hp.createConfigurable(psiFile);
          if (configurable != null) {
            list.add(configurable);
          }
        }
        myAdditionalPanels = list;

        for (HectorComponentPanel panel : myAdditionalPanels) {
          JComponent c;
          try {
            panel.reset();
            c = panel.createComponent();
          }
          catch (ProcessCanceledException e) {
            throw e;
          }
          catch (Throwable e) {
            //noinspection LoggerInitializedWithForeignClass
            Logger.getInstance(TrafficLightRenderer.class).error(e);
            continue;
          }

          if (c != null) {
            container.add(c, gc.nextLine().next().fillCellHorizontally().coverLine().weightx(1.0));
          }
        }
      }
    }

    @Override
    public boolean canClosePopup() {
      if (myAdditionalPanels.isEmpty()) {
        return true;
      }
      if (ContainerUtil.all(myAdditionalPanels, p -> p.canClose())) {
        PsiFile psiFile = getPsiFile();
        if (myAdditionalPanels.stream().filter(p -> p.isModified()).peek(TrafficLightRenderer::applyPanel).count() > 0) {
          if (psiFile != null) {
            InjectedLanguageManager.getInstance(getProject()).dropFileCaches(psiFile);
          }
          myDaemonCodeAnalyzer.restart();
        }
        return true;
      }
      return false;
    }

    @Override
    public void onClosePopup() {
      myAdditionalPanels.forEach(p -> p.disposeUIResources());
      myAdditionalPanels = Collections.emptyList();
    }

    @Override
    public void toggleProblemsView() {
      PsiFile file = getPsiFile();
      VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
      Document document = file == null ? null : file.getViewProvider().getDocument();
      ProblemsView.toggleCurrentFileProblems(getProject(), virtualFile, document);
    }
  }

  private static void applyPanel(@NotNull HectorComponentPanel panel) {
    try {
      panel.apply();
    }
    catch (ConfigurationException ignored) {}
  }

  protected class DefaultUIController extends AbstractUIController {
    private final List<AnAction> myMenuActions = initActions();

    private @NotNull List<AnAction> initActions() {
        List<AnAction> result = new ArrayList<>();
        result.add(new ConfigureInspectionsAction());
        result.add(DaemonEditorPopup.createGotoGroup());

        result.add(Separator.create());
        result.add(new ShowImportTooltipAction());

        return result;
    }

    @Override
    public @NotNull List<AnAction> getActions() {
      return myMenuActions;
    }

    @Override
    public boolean isToolbarEnabled() {
      return true;
    }

    // Actions shouldn't be anonymous classes for statistics reasons.
    private final class ShowImportTooltipAction extends ToggleAction {
      private ShowImportTooltipAction() {
        super(EditorBundle.message("iw.show.import.tooltip"));
      }

      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        PsiFile psiFile = getPsiFile();
        return psiFile != null && myDaemonCodeAnalyzer.isImportHintsEnabled(psiFile);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }


      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        PsiFile psiFile = getPsiFile();
        if (psiFile != null) {
          myDaemonCodeAnalyzer.setImportHintsEnabled(psiFile, state);
        }
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        PsiFile psiFile = getPsiFile();
        e.getPresentation().setEnabled(psiFile != null && myDaemonCodeAnalyzer.isAutohintsAvailable(psiFile));
      }

      @Override
      public boolean isDumbAware() {
        return true;
      }
    }
  }

  protected @NotNull UIController getUIController() {
    return myUIController;
  }

  void invalidate() {
    myHighlightingSettingsModificationCount = -1;
  }
}
