// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.ProjectTopics;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSettingListener;
import com.intellij.codeInsight.folding.impl.FoldingUtil;
import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerAdapter;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx;
import com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.TogglePopupHintsPanel;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;


/**
 * @author cdr
 */
public class DaemonListeners implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.DaemonListeners");

  private final Project myProject;
  private final DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;
  @NotNull private final PsiDocumentManager myPsiDocumentManager;
  private final FileEditorManager myFileEditorManager;
  private final UndoManager myUndoManager;
  private final ProjectLevelVcsManager myProjectLevelVcsManager;
  private final VcsDirtyScopeManager myVcsDirtyScopeManager;
  private final TooltipController myTooltipController;
  private final ErrorStripeUpdateManager myErrorStripeUpdateManager;

  private boolean myEscPressed;

  private volatile boolean cutOperationJustHappened;
  private final DaemonCodeAnalyzer.DaemonListener myDaemonEventPublisher;
  private List<Editor> myActiveEditors = Collections.emptyList();

  private static final Key<Boolean> DAEMON_INITIALIZED = Key.create("DAEMON_INITIALIZED");

  public static DaemonListeners getInstance(Project project) {
    return project.getComponent(DaemonListeners.class);
  }

  public DaemonListeners(@NotNull final Project project,
                         @NotNull DaemonCodeAnalyzerImpl daemonCodeAnalyzer,
                         @NotNull final EditorTracker editorTracker,
                         @NotNull EditorFactory editorFactory,
                         @NotNull PsiDocumentManager psiDocumentManager,
                         @NotNull final Application application,
                         @NotNull ProjectInspectionProfileManager inspectionProjectProfileManager,
                         @NotNull TodoConfiguration todoConfiguration,
                         @NotNull ActionManagerEx actionManager,
                         @NotNull final FileDocumentManager fileDocumentManager,
                         @NotNull final PsiManager psiManager,
                         @NotNull final FileEditorManager fileEditorManager,
                         @NotNull TooltipController tooltipController,
                         @NotNull UndoManager undoManager,
                         @NotNull ProjectLevelVcsManager projectLevelVcsManager,
                         @NotNull VcsDirtyScopeManager vcsDirtyScopeManager,
                         @NotNull ErrorStripeUpdateManager stripeUpdateManager) {
    myProject = project;
    myDaemonCodeAnalyzer = daemonCodeAnalyzer;
    myPsiDocumentManager = psiDocumentManager;
    myFileEditorManager = fileEditorManager;
    myUndoManager = undoManager;
    myProjectLevelVcsManager = projectLevelVcsManager;
    myVcsDirtyScopeManager = vcsDirtyScopeManager;
    myTooltipController = tooltipController;
    myErrorStripeUpdateManager = stripeUpdateManager;

    boolean replaced = ((UserDataHolderEx)myProject).replace(DAEMON_INITIALIZED, null, Boolean.TRUE);
    if (!replaced) {
      LOG.error("Daemon listeners already initialized for the project " + myProject);
    }

    MessageBus messageBus = myProject.getMessageBus();
    myDaemonEventPublisher = messageBus.syncPublisher(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC);
    if (project.isDefault()) return;

    MessageBusConnection connection = messageBus.connect(this);
    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appClosing() {
        stopDaemon(false, "App closing");
      }
    });

    EditorEventMulticaster eventMulticaster = editorFactory.getEventMulticaster();
    eventMulticaster.addDocumentListener(new DocumentListener() {
      // clearing highlighters before changing document because change can damage editor highlighters drastically, so we'll clear more than necessary
      @Override
      public void beforeDocumentChange(@NotNull final DocumentEvent e) {
        Document document = e.getDocument();
        VirtualFile virtualFile = fileDocumentManager.getFile(document);
        Project project = virtualFile == null ? null : ProjectUtil.guessProjectForFile(virtualFile);
        //no need to stop daemon if something happened in the console or in non-physical document
        if (worthBothering(document, project) && application.isDispatchThread()) {
          stopDaemon(true, "Document change");
          UpdateHighlightersUtil.updateHighlightersByTyping(myProject, e);
        }
      }
    }, this);

    eventMulticaster.addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent e) {
        final Editor editor = e.getEditor();
        if ((editor.getComponent().isShowing() || application.isHeadlessEnvironment()) &&
            worthBothering(editor.getDocument(), editor.getProject())) {

          if (!application.isUnitTestMode()) {
            ApplicationManager.getApplication().invokeLater(() -> {
              if ((editor.getComponent().isShowing() || application.isHeadlessEnvironment()) && !myProject.isDisposed()) {
                IntentionsUI.getInstance(myProject).invalidate();
              }
            }, ModalityState.current());
          }
        }
      }
    }, this);
    eventMulticaster.addEditorMouseMotionListener(new MyEditorMouseMotionListener(), this);
    eventMulticaster.addEditorMouseListener(new MyEditorMouseListener(myTooltipController), this);

    editorTracker.addEditorTrackerListener(this, __ -> {
      List<Editor> activeEditors = editorTracker.getActiveEditors();
      if (myActiveEditors.equals(activeEditors)) {
        return;
      }
      myActiveEditors = activeEditors;
      stopDaemon(true, "Active editor change");  // do not stop daemon if idea loses/gains focus
      if (ApplicationManager.getApplication().isDispatchThread() && LaterInvocator.isInModalContext()) {
        // editor appear in modal context, re-enable the daemon
        myDaemonCodeAnalyzer.setUpdateByTimerEnabled(true);
      }
      for (Editor editor : activeEditors) {
        myErrorStripeUpdateManager.repaintErrorStripePanel(editor);
      }
    });

    editorFactory.addEditorFactoryListener(new EditorFactoryListener() {
      @Override
      public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        Document document = editor.getDocument();
        Project editorProject = editor.getProject();
        // worthBothering() checks for getCachedPsiFile, so call getPsiFile here
        PsiFile file = editorProject == null ? null : PsiDocumentManager.getInstance(editorProject).getPsiFile(document);
        boolean showing = editor.getComponent().isShowing();
        boolean worthBothering = worthBothering(document, editorProject);
        if (!showing || !worthBothering) {
          LOG.debug("Not worth bothering about editor created for : " + file + " because editor isShowing(): " +
                    showing + "; project is open and file is mine: " + worthBothering);
          return;
        }
        myErrorStripeUpdateManager.repaintErrorStripePanel(editor);
      }

      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        // mem leak after closing last editor otherwise
        UIUtil.invokeLaterIfNeeded(IntentionsUI.getInstance(myProject)::invalidate);
      }
    }, this);

    PsiDocumentManagerImpl documentManager = (PsiDocumentManagerImpl)psiDocumentManager;
    PsiChangeHandler changeHandler = new PsiChangeHandler(myProject, documentManager, editorFactory, connection, daemonCodeAnalyzer.getFileStatusMap());
    Disposer.register(this, changeHandler);
    psiManager.addPsiTreeChangeListener(changeHandler, changeHandler);

    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        stopDaemonAndRestartAllFiles("Project roots changed");
      }
    });

    connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        stopDaemonAndRestartAllFiles("Dumb mode started");
      }

      @Override
      public void exitDumbMode() {
        stopDaemonAndRestartAllFiles("Dumb mode finished");
      }
    });

    connection.subscribe(PowerSaveMode.TOPIC, () -> stopDaemon(true, "Power save mode change"));
    connection.subscribe(EditorColorsManager.TOPIC, __ -> stopDaemonAndRestartAllFiles("Editor color scheme changed"));
    connection.subscribe(CommandListener.TOPIC, new MyCommandListener(actionManager));

    application.addApplicationListener(new MyApplicationListener(), this);
    inspectionProjectProfileManager.addProfileChangeListener(new MyProfileChangeListener(), this);

    connection.subscribe(TodoConfiguration.PROPERTY_CHANGE, new MyTodoListener());
    todoConfiguration.colorSettingsChanged();

    connection.subscribe(AnActionListener.TOPIC, new MyAnActionListener(actionManager));
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        boolean isDaemonShouldBeStopped = false;
        for (VFileEvent event : events) {
          if (event instanceof VFilePropertyChangeEvent) {
            VFilePropertyChangeEvent e = (VFilePropertyChangeEvent)event;
            String propertyName = e.getPropertyName();
            if (VirtualFile.PROP_NAME.equals(propertyName)) {
              fileRenamed(e);
            }
            if (!isDaemonShouldBeStopped && !propertyName.equals(PsiTreeChangeEvent.PROP_WRITABLE)) {
              isDaemonShouldBeStopped = true;
            }
          }
        }

        if (isDaemonShouldBeStopped) {
          stopDaemon(true, "Virtual file property change");
        }
      }

      private void fileRenamed(@NotNull VFilePropertyChangeEvent event) {
        stopDaemonAndRestartAllFiles("Virtual file name changed");
        VirtualFile virtualFile = event.getFile();
        PsiFile psiFile = !virtualFile.isValid() ? null : ((PsiManagerEx)psiManager).getFileManager().getCachedPsiFile(virtualFile);
        if (psiFile == null || myDaemonCodeAnalyzer.isHighlightingAvailable(psiFile)) {
          return;
        }

        Document document = fileDocumentManager.getCachedDocument(virtualFile);
        if (document == null) {
          return;
        }

        // highlight markers no more
        //todo clear all highlights regardless the pass id

        // Here color scheme required for TextEditorFields, as far as I understand this
        // code related to standard file editors, which always use Global color scheme,
        // thus we can pass null here.
        UpdateHighlightersUtil.setHighlightersToEditor(myProject, document, 0, document.getTextLength(),
                                                       Collections.emptyList(),
                                                       null,
                                                       Pass.UPDATE_ALL);
      }
    });

    ((EditorEventMulticasterEx)eventMulticaster).addErrorStripeListener(e -> {
      RangeHighlighter highlighter = e.getHighlighter();
      if (!highlighter.isValid()) return;
      HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
      if (info != null) {
        GotoNextErrorHandler.navigateToError(myProject, e.getEditor(), info);
      }
    }, this);

    ModalityStateListener modalityStateListener = __ -> {
      // before showing dialog we are in non-modal context yet, and before closing dialog we are still in modal context
      boolean inModalContext = Registry.is("ide.perProjectModality") || LaterInvocator.isInModalContext();
      stopDaemon(inModalContext, "Modality change. Was modal: " + inModalContext);
      myDaemonCodeAnalyzer.setUpdateByTimerEnabled(inModalContext);
    };
    LaterInvocator.addModalityStateListener(modalityStateListener,this);

    connection.subscribe(SeverityRegistrar.SEVERITIES_CHANGED_TOPIC, () -> stopDaemonAndRestartAllFiles("Severities changed"));

    if (RefResolveService.ENABLED) {
      RefResolveService resolveService = RefResolveService.getInstance(project);
      resolveService.addListener(this, new RefResolveService.Listener() {
        @Override
        public void allFilesResolved() {
          stopDaemon(true, "RefResolveService is up to date");
        }
      });
    }

    connection.subscribe(FacetManager.FACETS_TOPIC, new FacetManagerAdapter() {
      @Override
      public void facetRenamed(@NotNull Facet facet, @NotNull String oldName) {
        stopDaemonAndRestartAllFiles("facet renamed: " + oldName + " -> " + facet.getName());
      }

      @Override
      public void facetAdded(@NotNull Facet facet) {
        stopDaemonAndRestartAllFiles("facet added: " + facet.getName());
      }

      @Override
      public void facetRemoved(@NotNull Facet facet) {
        stopDaemonAndRestartAllFiles("facet removed: " + facet.getName());
      }

      @Override
      public void facetConfigurationChanged(@NotNull Facet facet) {
        stopDaemonAndRestartAllFiles("facet changed: " + facet.getName());
      }
    });

    connection.subscribe(FileHighlightingSettingListener.SETTING_CHANGE, (__, ___) -> updateStatusBar());
  }

  private boolean worthBothering(final Document document, Project project) {
    if (document == null) return true;
    if (project != null && project != myProject) return false;
    // cached is essential here since we do not want to create PSI file in alien project
    PsiFile psiFile = myPsiDocumentManager.getCachedPsiFile(document);
    return psiFile != null && psiFile.isPhysical() && psiFile.getOriginalFile() == psiFile;
  }

  @Override
  public void dispose() {
    stopDaemonAndRestartAllFiles("Project closed");
    boolean replaced = ((UserDataHolderEx)myProject).replace(DAEMON_INITIALIZED, Boolean.TRUE, Boolean.FALSE);
    LOG.assertTrue(replaced, "Daemon listeners already disposed for the project "+myProject);
  }

  public static boolean canChangeFileSilently(@NotNull PsiFileSystemItem file) {
    Project project = file.getProject();
    DaemonListeners listeners = getInstance(project);
    if (listeners == null) return true;

    if (listeners.cutOperationJustHappened) return false;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return false;
    if (file instanceof PsiCodeFragment) return true;
    if (ScratchUtil.isScratch(virtualFile)) return listeners.canUndo(virtualFile);
    if (!ModuleUtilCore.projectContainsFile(project, virtualFile, false)) return false;
    Result vcs = listeners.vcsThinksItChanged(virtualFile);
    if (vcs == Result.CHANGED) return true;
    if (vcs == Result.UNCHANGED) return false;

    return listeners.canUndo(virtualFile);
  }

  private boolean canUndo(@NotNull VirtualFile virtualFile) {
    for (FileEditor editor : myFileEditorManager.getEditors(virtualFile)) {
      if (myUndoManager.isUndoAvailable(editor)) return true;
    }
    return false;
  }

  private enum Result {
    CHANGED, UNCHANGED, NOT_SURE
  }

  @NotNull
  private Result vcsThinksItChanged(@NotNull VirtualFile virtualFile) {
    AbstractVcs activeVcs = myProjectLevelVcsManager.getVcsFor(virtualFile);
    if (activeVcs == null) return Result.NOT_SURE;

    FilePath path = VcsUtil.getFilePath(virtualFile);
    boolean vcsIsThinking = !myVcsDirtyScopeManager.whatFilesDirty(Collections.singletonList(path)).isEmpty();
    if (vcsIsThinking) return Result.NOT_SURE; // do not modify file which is in the process of updating

    FileStatus status = FileStatusManager.getInstance(myProject).getStatus(virtualFile);
    if (status == FileStatus.UNKNOWN) return Result.NOT_SURE;
    return status == FileStatus.MODIFIED || status == FileStatus.ADDED ? Result.CHANGED : Result.UNCHANGED;
  }

  private class MyApplicationListener implements ApplicationListener {
    private boolean myDaemonWasRunning;

    @Override
    public void beforeWriteActionStart(@NotNull Object action) {
      myDaemonWasRunning = myDaemonCodeAnalyzer.isRunning();
      if (!myDaemonWasRunning) return; // we'll restart in writeActionFinished()
      stopDaemon(true, "Write action start");
    }

    @Override
    public void writeActionFinished(@NotNull Object action) {
      stopDaemon(true, "Write action finish");
    }
  }

  private class MyCommandListener implements CommandListener {
    private final String myCutActionName;

    private MyCommandListener(@NotNull ActionManager actionManager) {
      myCutActionName = actionManager.getAction(IdeActions.ACTION_EDITOR_CUT).getTemplatePresentation().getText();
    }

    @Override
    public void commandStarted(@NotNull CommandEvent event) {
      Document affectedDocument = extractDocumentFromCommand(event);
      if (!worthBothering(affectedDocument, event.getProject())) return;

      cutOperationJustHappened = myCutActionName.equals(event.getCommandName());
      if (!myDaemonCodeAnalyzer.isRunning()) return;
      if (LOG.isDebugEnabled()) {
        LOG.debug("cancelling code highlighting by command:" + event.getCommand());
      }
      stopDaemon(false, "Command start");
    }

    @Nullable
    private Document extractDocumentFromCommand(@NotNull CommandEvent event) {
      Document affectedDocument = event.getDocument();
      if (affectedDocument != null) return affectedDocument;
      Object id = event.getCommandGroupId();

      if (id instanceof Document) {
        affectedDocument = (Document)id;
      }
      else if (id instanceof DocCommandGroupId) {
        affectedDocument = ((DocCommandGroupId)id).getDocument();
      }
      return affectedDocument;
    }

    @Override
    public void commandFinished(@NotNull CommandEvent event) {
      Document affectedDocument = extractDocumentFromCommand(event);
      if (!worthBothering(affectedDocument, event.getProject())) return;

      if (myEscPressed) {
        myEscPressed = false;
        if (affectedDocument != null) {
          // prevent Esc key to leave the document in the not-highlighted state
          if (!myDaemonCodeAnalyzer.getFileStatusMap().allDirtyScopesAreNull(affectedDocument)) {
            stopDaemon(true, "Command finish");
          }
        }
      }
      else if (!myDaemonCodeAnalyzer.isRunning()) {
        stopDaemon(true, "Command finish");
      }
    }
  }

  private class MyTodoListener implements PropertyChangeListener {
    @Override
    public void propertyChange(@NotNull PropertyChangeEvent evt) {
      if (TodoConfiguration.PROP_TODO_PATTERNS.equals(evt.getPropertyName())) {
        stopDaemonAndRestartAllFiles("Todo patterns changed");
      }
      else if (TodoConfiguration.PROP_MULTILINE.equals(evt.getPropertyName())) {
        stopDaemonAndRestartAllFiles("Todo multi-line detection changed");
      }
    }
  }

  private class MyProfileChangeListener implements ProfileChangeAdapter {
    @Override
    public void profileChanged(InspectionProfile profile) {
      stopDaemonAndRestartAllFiles("Profile changed");
      updateStatusBarLater();
    }

    @Override
    public void profileActivated(InspectionProfile oldProfile, @Nullable InspectionProfile profile) {
      stopDaemonAndRestartAllFiles("Profile activated");
      updateStatusBarLater();
    }

    @Override
    public void profilesInitialized() {
      UIUtil.invokeLaterIfNeeded(() -> {
        if (myProject.isDisposed()) return;
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
        myTogglePopupHintsPanel = new TogglePopupHintsPanel(myProject);
        statusBar.addWidget(myTogglePopupHintsPanel, myProject);
        updateStatusBar();

        stopDaemonAndRestartAllFiles("Inspection profiles activated");
      });
    }
  }

  private TogglePopupHintsPanel myTogglePopupHintsPanel;

  void updateStatusBar() {
    if (myTogglePopupHintsPanel != null) myTogglePopupHintsPanel.updateStatus();
  }

  private void updateStatusBarLater() {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myProject.isDisposed()) return;
      updateStatusBar();
    });
  }

  private class MyAnActionListener implements AnActionListener {
    private final AnAction escapeAction;

    private MyAnActionListener(@NotNull ActionManager actionManager) {
      escapeAction = actionManager.getAction(IdeActions.ACTION_EDITOR_ESCAPE);
    }

    @Override
    public void beforeActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
      myEscPressed = action == escapeAction;
    }

    @Override
    public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
      Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      //no need to stop daemon if something happened in the console
      if (editor != null && !worthBothering(editor.getDocument(), editor.getProject())) {
        return;
      }
      stopDaemon(true, "Editor typing");
    }
  }

  private static class MyEditorMouseListener implements EditorMouseListener {
    @NotNull
    private final TooltipController myTooltipController;

    MyEditorMouseListener(@NotNull TooltipController tooltipController) {
      myTooltipController = tooltipController;
    }

    @Override
    public void mouseExited(@NotNull EditorMouseEvent e) {
      if (!myTooltipController.shouldSurvive(e.getMouseEvent())) {
        DaemonTooltipUtil.cancelTooltips();
      }
    }
  }

  private class MyEditorMouseMotionListener implements EditorMouseMotionListener {
    @Override
    public void mouseMoved(@NotNull EditorMouseEvent e) {
      if (Registry.is("ide.disable.editor.tooltips")) {
        return;
      }
      Editor editor = e.getEditor();
      if (myProject != editor.getProject()) return;
      if (EditorMouseHoverPopupControl.arePopupsDisabled(editor)) return;

      boolean shown = false;
      try {
        // There is a possible case that cursor is located at soft wrap-introduced virtual space (that is mapped to offset
        // of the document symbol just after soft wrap). We don't want to show any tooltips for it then.
        VisualPosition visual = editor.xyToVisualPosition(e.getMouseEvent().getPoint());
        if (editor.getSoftWrapModel().isInsideOrBeforeSoftWrap(visual)) {
          return;
        }
        LogicalPosition logical = editor.visualToLogicalPosition(visual);
        if (e.getArea() == EditorMouseEventArea.EDITING_AREA && !UIUtil.isControlKeyDown(e.getMouseEvent())) {
          int offset = editor.logicalPositionToOffset(logical);
          if (editor.offsetToLogicalPosition(offset).column != logical.column) return; // we are in virtual space
          if (editor.getInlayModel().getElementAt(e.getMouseEvent().getPoint()) != null) return;
          HighlightInfo info = myDaemonCodeAnalyzer.findHighlightByOffset(editor.getDocument(), offset, false);
          if (info == null || info.getDescription() == null ||
              info.getHighlighter() != null && FoldingUtil.isHighlighterFolded(editor, info.getHighlighter())) {
            IdeTooltipManager.getInstance().hideCurrent(e.getMouseEvent());
            return;
          }
          DaemonTooltipUtil.showInfoTooltip(info, editor, offset);
          shown = true;
        }
      }
      finally {
        if (!shown && !myTooltipController.shouldSurvive(e.getMouseEvent())) {
          DaemonTooltipUtil.cancelTooltips();
        }
      }
    }

    @Override
    public void mouseDragged(@NotNull EditorMouseEvent e) {
      myTooltipController.cancelTooltips();
    }
  }

  private void stopDaemon(boolean toRestartAlarm, @NonNls @NotNull String reason) {
    if (myDaemonCodeAnalyzer.stopProcess(toRestartAlarm, reason)) {
      myDaemonEventPublisher.daemonCancelEventOccurred(reason);
    }
  }

  private void stopDaemonAndRestartAllFiles(@NotNull String reason) {
    if (myDaemonCodeAnalyzer.doRestart()) {
      myDaemonEventPublisher.daemonCancelEventOccurred(reason);
    }
  }
}
