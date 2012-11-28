/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.daemon.impl;

import com.intellij.ProjectTopics;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;


/**
 * @author cdr
 */
public class DaemonListeners implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.DaemonListeners");
  @NonNls public static final Object IGNORE_MOUSE_TRACKING = "ignore_mouse_tracking";

  private final Project myProject;
  private final DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;
  @NotNull private final PsiDocumentManager myPsiDocumentManager;
  private final FileEditorManager myFileEditorManager;
  private final UndoManager myUndoManager;
  private final ProjectLevelVcsManager myProjectLevelVcsManager;
  private final VcsDirtyScopeManager myVcsDirtyScopeManager;
  private final FileStatusManager myFileStatusManager;
  private final ActionManager myActionManager;
  private final TooltipController myTooltipController;

  private boolean myEscPressed;

  private volatile boolean cutOperationJustHappened;
  private final DaemonCodeAnalyzer.DaemonListener myDaemonEventPublisher;

  private static final Key<Boolean> DAEMON_INITIALIZED = Key.create("DAEMON_INITIALIZED");

  public static DaemonListeners getInstance(Project project) {
    return project.getComponent(DaemonListeners.class);
  }

  public DaemonListeners(@NotNull Project project,
                         @NotNull DaemonCodeAnalyzerImpl daemonCodeAnalyzer,
                         @NotNull final EditorTracker editorTracker,
                         @NotNull EditorFactory editorFactory,
                         @NotNull PsiDocumentManager psiDocumentManager,
                         @NotNull CommandProcessor commandProcessor,
                         @NotNull EditorColorsManager editorColorsManager,
                         @NotNull final Application application,
                         @NotNull InspectionProfileManager inspectionProfileManager,
                         @NotNull InspectionProjectProfileManager inspectionProjectProfileManager,
                         @NotNull TodoConfiguration todoConfiguration,
                         @NotNull ActionManagerEx actionManagerEx,
                         @NotNull VirtualFileManager virtualFileManager,
                         @NotNull final NamedScopeManager namedScopeManager,
                         @NotNull final DependencyValidationManager dependencyValidationManager,
                         @NotNull final FileDocumentManager fileDocumentManager,
                         @NotNull final PsiManager psiManager,
                         @NotNull final FileEditorManager fileEditorManager,
                         @NotNull TooltipController tooltipController,
                         @NotNull UndoManager undoManager,
                         @NotNull ProjectLevelVcsManager projectLevelVcsManager,
                         @NotNull VcsDirtyScopeManager vcsDirtyScopeManager,
                         @NotNull FileStatusManager fileStatusManager) {
    myProject = project;
    myDaemonCodeAnalyzer = daemonCodeAnalyzer;
    myPsiDocumentManager = psiDocumentManager;
    myFileEditorManager = fileEditorManager;
    myUndoManager = undoManager;
    myProjectLevelVcsManager = projectLevelVcsManager;
    myVcsDirtyScopeManager = vcsDirtyScopeManager;
    myFileStatusManager = fileStatusManager;
    myActionManager = actionManagerEx;
    myTooltipController = tooltipController;

    boolean replaced = ((UserDataHolderEx)myProject).replace(DAEMON_INITIALIZED, null, Boolean.TRUE);
    LOG.assertTrue(replaced, "Daemon listeners already initialized for the project "+myProject);

    MessageBus messageBus = myProject.getMessageBus();
    myDaemonEventPublisher = messageBus.syncPublisher(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC);
    final MessageBusConnection connection = messageBus.connect();

    if (project.isDefault()) return;
    EditorEventMulticaster eventMulticaster = editorFactory.getEventMulticaster();
    eventMulticaster.addDocumentListener(new DocumentAdapter() {
      // clearing highlighters before changing document because change can damage editor highlighters drastically, so we'll clear more than necessary
      @Override
      public void beforeDocumentChange(final DocumentEvent e) {
        if (isUnderIgnoredAction(null)) return;
        Document document = e.getDocument();
        VirtualFile virtualFile = fileDocumentManager.getFile(document);
        Project project = virtualFile == null ? null : ProjectUtil.guessProjectForFile(virtualFile);
        if (!worthBothering(document, project)) {
          return; //no need to stop daemon if something happened in the console
        }
        stopDaemon(true, "Document change");
        UpdateHighlightersUtil.updateHighlightersByTyping(myProject, e);
      }
    }, this);

    eventMulticaster.addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(CaretEvent e) {
        Editor editor = e.getEditor();
        if (!editor.getComponent().isShowing() && !application.isUnitTestMode() ||
            !worthBothering(editor.getDocument(), editor.getProject())) {
          return; //no need to stop daemon if something happened in the console
        }

        stopDaemon(true, "Caret move");
        myDaemonCodeAnalyzer.hideLastIntentionHint();
      }
    }, this);

    eventMulticaster.addEditorMouseMotionListener(new MyEditorMouseMotionListener(), this);
    eventMulticaster.addEditorMouseListener(new MyEditorMouseListener(myTooltipController), this);

    EditorTrackerListener editorTrackerListener = new EditorTrackerListener() {
      private List<Editor> myActiveEditors = Collections.emptyList();
      @Override
      public void activeEditorsChanged(@NotNull List<Editor> editors) {
        List<Editor> activeEditors = editorTracker.getActiveEditors();
        if (myActiveEditors.equals(activeEditors)) {
          return;
        }
        myActiveEditors = activeEditors;
        stopDaemon(true, "Active editor change");  // do not stop daemon if idea loses/gains focus
        if (LaterInvocator.isInModalContext()) {
          // editor appear in modal context, re-enable the daemon
          myDaemonCodeAnalyzer.setUpdateByTimerEnabled(true);
        }
        for (Editor editor : activeEditors) {
          myDaemonCodeAnalyzer.repaintErrorStripeRenderer(editor);
        }
      }
    };
    editorTracker.addEditorTrackerListener(editorTrackerListener, this);

    EditorFactoryListener editorFactoryListener = new EditorFactoryListener() {
      @Override
      public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        Document document = editor.getDocument();
        Project editorProject = editor.getProject();
        // worthBothering() checks for getCachedPsiFile, so call getPsiFile here
        PsiFile file = editorProject == null ? null : PsiDocumentManager.getInstance(editorProject).getPsiFile(document);
        if (!editor.getComponent().isShowing() || !worthBothering(document, editorProject)) {
          LOG.debug("Not worth: " + file);
          return;
        }
        myDaemonCodeAnalyzer.repaintErrorStripeRenderer(editor);
      }

      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        // mem leak after closing last editor otherwise
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            myDaemonCodeAnalyzer.hideLastIntentionHint();
          }
        });
      }
    };
    editorFactory.addEditorFactoryListener(editorFactoryListener, this);

    PsiDocumentManagerImpl documentManager = (PsiDocumentManagerImpl)psiDocumentManager;
    PsiChangeHandler changeHandler = new PsiChangeHandler(myProject, documentManager, editorFactory,connection,
                                                          daemonCodeAnalyzer.getFileStatusMap());
    Disposer.register(this, changeHandler);
    psiManager.addPsiTreeChangeListener(changeHandler, changeHandler);

    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        final FileEditor[] editors = fileEditorManager.getSelectedEditors();
        if (editors.length == 0) return;
        application.invokeLater(new Runnable() {
          @Override
          public void run() {
            if (myProject.isDisposed()) return;
            for (FileEditor fileEditor : editors) {
              if (fileEditor instanceof TextEditor) {
                myDaemonCodeAnalyzer.repaintErrorStripeRenderer(((TextEditor)fileEditor).getEditor());
              }
            }
          }
        }, ModalityState.stateForComponent(editors[0].getComponent()));
      }
    });

    connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        stopDaemonAndRestartAllFiles();
      }

      @Override
      public void exitDumbMode() {
        stopDaemonAndRestartAllFiles();
      }
    });

    connection.subscribe(PowerSaveMode.TOPIC, new PowerSaveMode.Listener() {
      @Override
      public void powerSaveStateChanged() {
        stopDaemon(true, "Power save mode change");
      }
    });


    commandProcessor.addCommandListener(new MyCommandListener(), this);
    application.addApplicationListener(new MyApplicationListener(), this);
    editorColorsManager.addEditorColorsListener(new MyEditorColorsListener(), this);
    inspectionProfileManager.addProfileChangeListener(new MyProfileChangeListener(), this);
    inspectionProjectProfileManager.addProfilesListener(new MyProfileChangeListener(), this);
    todoConfiguration.addPropertyChangeListener(new MyTodoListener(), this);
    actionManagerEx.addAnActionListener(new MyAnActionListener(), this);
    virtualFileManager.addVirtualFileListener(new VirtualFileAdapter() {
      @Override
      public void propertyChanged(VirtualFilePropertyEvent event) {
        String propertyName = event.getPropertyName();
        if (VirtualFile.PROP_NAME.equals(propertyName)) {
          stopDaemonAndRestartAllFiles();
          VirtualFile virtualFile = event.getFile();
          PsiFile psiFile = ((PsiManagerEx)psiManager).getFileManager().getCachedPsiFile(virtualFile);
          if (psiFile != null && !myDaemonCodeAnalyzer.isHighlightingAvailable(psiFile)) {
            Document document = fileDocumentManager.getCachedDocument(virtualFile);
            if (document != null) {
              // highlight markers no more
              //todo clear all highlights regardless the pass id

              // Here color scheme required for TextEditorFields, as far as I understand this
              // code related to standard file editors, which always use Global color scheme,
              // thus we can pass null here.
              final EditorColorsScheme editorColorScheme = null;

              UpdateHighlightersUtil.setHighlightersToEditor(myProject, document, 0, document.getTextLength(),
                                                             Collections.<HighlightInfo>emptyList(),
                                                             editorColorScheme,
                                                             Pass.UPDATE_ALL);
            }
          }
        }
        if (!propertyName.equals(PsiTreeChangeEvent.PROP_WRITABLE)) {
          stopDaemon(true, "Virtual file property change");
        }
      }
    }, this);

    ((EditorEventMulticasterEx)eventMulticaster).addErrorStripeListener(new ErrorStripeHandler(myProject), this);

    Set<NamedScopesHolder> holders = new THashSet<NamedScopesHolder>(Arrays.asList(NamedScopesHolder.getAllNamedScopeHolders(project)));
    // to ensure initialization dependency
    holders.add(namedScopeManager);
    holders.add(dependencyValidationManager);

    NamedScopesHolder.ScopeListener scopeListener = new NamedScopesHolder.ScopeListener() {
      @Override
      public void scopesChanged() {
        myDaemonCodeAnalyzer.reloadScopes(dependencyValidationManager, namedScopeManager);
      }
    };
    for (NamedScopesHolder holder : holders) {
      holder.addScopeListener(scopeListener);
    }

    ModalityStateListener modalityStateListener = new ModalityStateListener() {
      @Override
      public void beforeModalityStateChanged(boolean entering) {
        // before showing dialog we are in non-modal context yet, and before closing dialog we are still in modal context
        boolean inModalContext = LaterInvocator.isInModalContext();
        stopDaemon(inModalContext, "Modality change");
        myDaemonCodeAnalyzer.setUpdateByTimerEnabled(inModalContext);
      }
    };
    LaterInvocator.addModalityStateListener(modalityStateListener,this);
  }

  static boolean isUnderIgnoredAction(@Nullable Object action) {
    return action instanceof DocumentRunnable.IgnoreDocumentRunnable ||
           ApplicationManager.getApplication().hasWriteAction(DocumentRunnable.IgnoreDocumentRunnable.class);
  }

  private boolean worthBothering(final Document document, Project project) {
    if (document == null) return true;
    if (project != null && project != myProject) return false;
    // cached is essential here since we do not want to create PSI file in alien project
    PsiFile psiFile = myPsiDocumentManager.getCachedPsiFile(document);
    return psiFile != null && psiFile.getOriginalFile() == psiFile;
  }

  @Override
  public void dispose() {
    stopDaemonAndRestartAllFiles();
    boolean replaced = ((UserDataHolderEx)myProject).replace(DAEMON_INITIALIZED, Boolean.TRUE, Boolean.FALSE);
    LOG.assertTrue(replaced, "Daemon listeners already disposed for the project "+myProject);
  }

  boolean canChangeFileSilently(@NotNull PsiFileSystemItem file) {
    if (cutOperationJustHappened) return false;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return false;
    if (file instanceof PsiCodeFragment) return true;
    if (!ModuleUtilCore.projectContainsFile(myProject, virtualFile, false)) return false;
    Result vcs = vcsThinksItChanged(virtualFile);
    if (vcs == Result.CHANGED) return true;
    if (vcs == Result.UNCHANGED) return false;

    return canUndo(virtualFile);
  }

  private boolean canUndo(@NotNull VirtualFile virtualFile) {
    for (FileEditor editor : myFileEditorManager.getEditors(virtualFile)) {
      if (myUndoManager.isUndoAvailable(editor)) return true;
    }
    return false;
  }

  private static enum Result {
    CHANGED, UNCHANGED, NOT_SURE
  }

  private Result vcsThinksItChanged(VirtualFile virtualFile) {
    AbstractVcs activeVcs = myProjectLevelVcsManager.getVcsFor(virtualFile);
    if (activeVcs == null) return Result.NOT_SURE;
    
    FilePath path = new FilePathImpl(virtualFile);
    boolean vcsIsThinking = !myVcsDirtyScopeManager.whatFilesDirty(Arrays.asList(path)).isEmpty();
    if (vcsIsThinking) return Result.NOT_SURE; // do not modify file which is in the process of updating

    FileStatus status = myFileStatusManager.getStatus(virtualFile);
    if (status == FileStatus.UNKNOWN) return Result.NOT_SURE;
    return status == FileStatus.MODIFIED || status == FileStatus.ADDED ? Result.CHANGED : Result.UNCHANGED;
  }

  private class MyApplicationListener extends ApplicationAdapter {
    private boolean myDaemonWasRunning;

    @Override
    public void beforeWriteActionStart(Object action) {
      myDaemonWasRunning = myDaemonCodeAnalyzer.isRunning();
      if (!myDaemonWasRunning) return; // we'll restart in writeActionFinished()
      stopDaemon(true, "Write action start");
    }

    @Override
    public void writeActionFinished(Object action) {
      if (myDaemonWasRunning) {
        stopDaemon(true, "Write action finish");
      }
    }
  }

  private class MyCommandListener extends CommandAdapter {
    private final Object myCutActionName = myActionManager.getAction(IdeActions.ACTION_EDITOR_CUT).getTemplatePresentation().getText();

    @Override
    public void commandStarted(CommandEvent event) {
      Document affectedDocument = extractDocumentFromCommand(event);
      if (isUnderIgnoredAction(null)) return;
      if (!worthBothering(affectedDocument, event.getProject())) return;

      cutOperationJustHappened = myCutActionName.equals(event.getCommandName());
      if (!myDaemonCodeAnalyzer.isRunning()) return;
      if (LOG.isDebugEnabled()) {
        LOG.debug("cancelling code highlighting by command:" + event.getCommand());
      }
      stopDaemon(false, "Command start");
    }

    @Nullable
    private Document extractDocumentFromCommand(CommandEvent event) {
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
    public void commandFinished(CommandEvent event) {
      Document affectedDocument = extractDocumentFromCommand(event);
      if (isUnderIgnoredAction(null)) return;
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

  private class MyEditorColorsListener implements EditorColorsListener {
    @Override
    public void globalSchemeChange(EditorColorsScheme scheme) {
      stopDaemonAndRestartAllFiles();
    }
  }

  private class MyTodoListener implements PropertyChangeListener {
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      if (TodoConfiguration.PROP_TODO_PATTERNS.equals(evt.getPropertyName())) {
        stopDaemonAndRestartAllFiles();
      }
    }
  }

  private class MyProfileChangeListener extends ProfileChangeAdapter {
    @Override
    public void profileChanged(Profile profile) {
      stopDaemonAndRestartAllFiles();
    }

    @Override
    public void profileActivated(Profile oldProfile, Profile profile) {
      stopDaemonAndRestartAllFiles();
    }
  }

  private class MyAnActionListener implements AnActionListener {
    private final AnAction escapeAction = myActionManager.getAction(IdeActions.ACTION_EDITOR_ESCAPE);

    @Override
    public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
      myEscPressed = action == escapeAction;
    }

    @Override
    public void afterActionPerformed(final AnAction action, final DataContext dataContext, AnActionEvent event) {
    }

    @Override
    public void beforeEditorTyping(char c, DataContext dataContext) {
      Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
      //no need to stop daemon if something happened in the console
      if (editor != null && !worthBothering(editor.getDocument(), editor.getProject())) {
        return;
      }
      stopDaemon(true, "Editor typing");
    }
  }

  private static class MyEditorMouseListener extends EditorMouseAdapter {
    private final TooltipController myTooltipController;

    public MyEditorMouseListener(TooltipController tooltipController) {
      myTooltipController = tooltipController;
    }

    @Override
    public void mouseExited(EditorMouseEvent e) {
      if (!myTooltipController.shouldSurvive(e.getMouseEvent())) {
        DaemonTooltipUtil.cancelTooltips();
      }
    }
  }

  private class MyEditorMouseMotionListener implements EditorMouseMotionListener {
    @Override
    public void mouseMoved(EditorMouseEvent e) {
      Editor editor = e.getEditor();
      if (myProject != editor.getProject()) return;
      if (editor.getComponent().getClientProperty(IGNORE_MOUSE_TRACKING) != null) return;

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
          HighlightInfo info = myDaemonCodeAnalyzer.findHighlightByOffset(editor.getDocument(), offset, false);
          if (info == null || info.description == null) return;
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
    public void mouseDragged(EditorMouseEvent e) {
      myTooltipController.cancelTooltips();
    }
  }

  private void stopDaemon(boolean toRestartAlarm, @NonNls String reason) {
    myDaemonEventPublisher.daemonCancelEventOccurred();
    myDaemonCodeAnalyzer.stopProcess(toRestartAlarm, reason);
  }

  private void stopDaemonAndRestartAllFiles() {
    myDaemonEventPublisher.daemonCancelEventOccurred();
    myDaemonCodeAnalyzer.restart();
  }

}
