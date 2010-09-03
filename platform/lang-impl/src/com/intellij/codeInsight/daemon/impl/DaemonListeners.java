/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.command.impl.UndoManagerImpl;
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
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


/**
 * @author cdr
 */
class DaemonListeners implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.DaemonListeners");

  private final ApplicationListener myApplicationListener = new MyApplicationListener();
  private final EditorColorsListener myEditorColorsListener = new MyEditorColorsListener();
  private final PropertyChangeListener myTodoListener = new MyTodoListener();

  private final EditorFactoryListener myEditorFactoryListener;

  private final Project myProject;
  private final DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;
  private final ModalityStateListener myModalityStateListener;

  private boolean myEscPressed;

  private final ErrorStripeHandler myErrorStripeHandler;

  private volatile boolean cutOperationJustHappened;
  private final EditorTracker myEditorTracker;
  private final EditorTrackerListener myEditorTrackerListener;

  public DaemonListeners(Project project, DaemonCodeAnalyzerImpl daemonCodeAnalyzer, EditorTracker editorTracker) {
    myProject = project;
    myDaemonCodeAnalyzer = daemonCodeAnalyzer;

    final MessageBusConnection connection = myProject.getMessageBus().connect();
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();

    eventMulticaster.addDocumentListener(new DocumentAdapter() {
      // clearing highlighters before changing document because change can damage editor highlighters drastically, so we'll clear more than necessary
      public void beforeDocumentChange(final DocumentEvent e) {
        Document document = e.getDocument();
        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
        if (!worthBothering(document, virtualFile == null ? null : ProjectUtil.guessProjectForFile(virtualFile))) {
          return; //no need to stop daemon if something happened in the console
        }
        stopDaemon(true);
        UpdateHighlightersUtil.updateHighlightersByTyping(myProject, e);
      }
    }, this);

    eventMulticaster.addCaretListener(new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        Editor editor = e.getEditor();
        if (!worthBothering(editor.getDocument(), editor.getProject())) {
          return; //no need to stop daemon if something happened in the console
        }

        stopDaemon(true);
        myDaemonCodeAnalyzer.hideLastIntentionHint();
      }
    }, this);

    eventMulticaster.addEditorMouseMotionListener(new MyEditorMouseMotionListener(), this);
    eventMulticaster.addEditorMouseListener(new MyEditorMouseListener(), this);

    myEditorTracker = editorTracker;
    myEditorTrackerListener = new EditorTrackerListener() {
      private List<Editor> myActiveEditors = Collections.emptyList();

      public void activeEditorsChanged(@NotNull List<Editor> editors) {
        List<Editor> activeEditors = myEditorTracker.getActiveEditors();
        if (!myActiveEditors.equals(activeEditors)) {
          myActiveEditors = activeEditors;
          stopDaemon(true);  // do not stop daemon if idea loses/gains focus
        }
      }
    };
    myEditorTracker.addEditorTrackerListener(myEditorTrackerListener);

    myEditorFactoryListener = new EditorFactoryAdapter() {
      public void editorCreated(EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        Document document = editor.getDocument();
        if (!worthBothering(document, editor.getProject())) {
          return;
        }
        myDaemonCodeAnalyzer.repaintErrorStripeRenderer(editor);
      }
    };
    EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener);

    PsiDocumentManagerImpl documentManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myProject);
    PsiChangeHandler changeHandler = new PsiChangeHandler(myProject, daemonCodeAnalyzer, documentManager, EditorFactory.getInstance(),connection);
    Disposer.register(this, changeHandler);
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(changeHandler, changeHandler);

    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        final FileEditor[] editors = FileEditorManager.getInstance(myProject).getSelectedEditors();
        if (editors.length == 0) return;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
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

      public void enteredDumbMode() {
        stopDaemon(true);
      }

      public void exitDumbMode() {
        stopDaemon(true);
      }
    });

    connection.subscribe(PowerSaveMode.TOPIC, new PowerSaveMode.Listener() {
      @Override
      public void powerSaveStateChanged() {
        stopDaemon(true);
      }
    });

    CommandProcessor.getInstance().addCommandListener(new MyCommandListener(), this);
    ApplicationManager.getApplication().addApplicationListener(myApplicationListener);
    EditorColorsManager.getInstance().addEditorColorsListener(myEditorColorsListener);
    InspectionProfileManager.getInstance().addProfileChangeListener(new MyProfileChangeListener(), this);
    TodoConfiguration.getInstance().addPropertyChangeListener(myTodoListener);
    ActionManagerEx.getInstanceEx().addAnActionListener(new MyAnActionListener(), this);
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
      public void propertyChanged(VirtualFilePropertyEvent event) {
        if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
          myDaemonCodeAnalyzer.restart();
          PsiFile psiFile = PsiManager.getInstance(myProject).findFile(event.getFile());
          if (psiFile != null && !myDaemonCodeAnalyzer.isHighlightingAvailable(psiFile)) {
            Document document = FileDocumentManager.getInstance().getCachedDocument(event.getFile());
            if (document != null) {
              // highlight markers no more
              //todo clear all highlights regardless the pass id
              UpdateHighlightersUtil
                .setHighlightersToEditor(myProject, document, 0, document.getTextLength(), Collections.<HighlightInfo>emptyList(),
                                         Pass.UPDATE_ALL);
            }
          }
        }
      }
    }, this);

    myErrorStripeHandler = new ErrorStripeHandler(myProject);
    ((EditorEventMulticasterEx)eventMulticaster).addErrorStripeListener(myErrorStripeHandler);

    final NamedScopesHolder[] holders = NamedScopesHolder.getAllNamedScopeHolders(project);
    NamedScopesHolder.ScopeListener scopeListener = new NamedScopesHolder.ScopeListener() {
      public void scopesChanged() {
        myDaemonCodeAnalyzer.reloadScopes();
      }
    };
    for (NamedScopesHolder holder : holders) {
      holder.addScopeListener(scopeListener);
    }

    myModalityStateListener = new ModalityStateListener() {
      public void beforeModalityStateChanged(boolean entering) {
        // before showing dialog we are in non-modal context yet, and before closing dialog we are still in modal context
        stopDaemon(LaterInvocator.isInModalContext());
      }
    };
    LaterInvocator.addModalityStateListener(myModalityStateListener);
  }

  private boolean worthBothering(final Document document, Project project) {
    if (document == null) return true;
    if (project != null && project != myProject) return false;
    // cached is essential here since we do not want to create PSI file in alien project
    PsiFile psiFile = PsiDocumentManager.getInstance(project == null ? myProject : project).getPsiFile(document);
    return psiFile != null;
  }

  public void dispose() {
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();

    EditorFactory.getInstance().removeEditorFactoryListener(myEditorFactoryListener);
    ApplicationManager.getApplication().removeApplicationListener(myApplicationListener);
    EditorColorsManager.getInstance().removeEditorColorsListener(myEditorColorsListener);
    TodoConfiguration.getInstance().removePropertyChangeListener(myTodoListener);

    ((EditorEventMulticasterEx)eventMulticaster).removeErrorStripeListener(myErrorStripeHandler);
    myEditorTracker.removeEditorTrackerListener(myEditorTrackerListener);
    LaterInvocator.removeModalityStateListener(myModalityStateListener);
  }

  boolean canChangeFileSilently(PsiFileSystemItem file) {
    if (cutOperationJustHappened) return false;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return false;
    if (file instanceof PsiCodeFragment) return true;
    Project project = file.getProject();
    if (!ModuleUtil.projectContainsFile(project, virtualFile, false)) return false;
    Result vcs = vcsThinksItChanged(virtualFile, project);
    if (vcs == Result.CHANGED) return true;
    if (vcs == Result.UNCHANGED) return false;

    return canUndo(virtualFile);
  }

  private boolean canUndo(VirtualFile virtualFile) {
    for (FileEditor editor : FileEditorManager.getInstance(myProject).getEditors(virtualFile)) {
      if (UndoManagerImpl.getInstance(myProject).isUndoAvailable(editor)) return true;
    }
    return false;
  }

  private static enum Result {
    CHANGED, UNCHANGED, NOT_SURE
  }
  private Result vcsThinksItChanged(VirtualFile virtualFile, Project project) {
    AbstractVcs activeVcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(virtualFile);
    if (activeVcs == null) return Result.NOT_SURE;
    
    FilePath path = new FilePathImpl(virtualFile);
    boolean vcsIsThinking = !VcsDirtyScopeManager.getInstance(myProject).whatFilesDirty(Arrays.asList(path)).isEmpty();
    if (vcsIsThinking) return Result.UNCHANGED; // do not modify file which is in the process of updating

    FileStatus status = FileStatusManager.getInstance(project).getStatus(virtualFile);

    return status == FileStatus.MODIFIED || status == FileStatus.ADDED ? Result.CHANGED : Result.UNCHANGED;
  }

  private class MyApplicationListener extends ApplicationAdapter {
    public void beforeWriteActionStart(Object action) {
      if (!myDaemonCodeAnalyzer.isRunning()) return;
      if (LOG.isDebugEnabled()) {
        LOG.debug("cancelling code highlighting by write action:" + action);
      }
      if (containsDocumentWorthBothering(action)) {
        stopDaemon(false);
      }
    }

    private boolean containsDocumentWorthBothering(Object action) {
      if (action instanceof DocumentRunnable) {
        if (action instanceof DocumentRunnable.IgnoreDocumentRunnable) return false;
        Document document = ((DocumentRunnable)action).getDocument();
        if (!worthBothering(document, ((DocumentRunnable)action).getProject())) {
          return false;
        }
      }
      return true;
    }

    public void writeActionFinished(Object action) {
      if (myDaemonCodeAnalyzer.isRunning()) return;
      if (containsDocumentWorthBothering(action)) {
        stopDaemon(true);
      }
    }
  }

  private class MyCommandListener extends CommandAdapter {
    private final Object myCutActionName =
      ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_CUT).getTemplatePresentation().getText();

    public void commandStarted(CommandEvent event) {
      Document affectedDocument = extractDocumentFromCommand(event);
      if (!worthBothering(affectedDocument, event.getProject())) {
        return;
      }

      cutOperationJustHappened = myCutActionName.equals(event.getCommandName());
      if (!myDaemonCodeAnalyzer.isRunning()) return;
      if (LOG.isDebugEnabled()) {
        LOG.debug("cancelling code highlighting by command:" + event.getCommand());
      }
      stopDaemon(false);
    }

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

    public void commandFinished(CommandEvent event) {
      Document affectedDocument = extractDocumentFromCommand(event);
      if (!worthBothering(affectedDocument, event.getProject())) {
        return;
      }

      if (myEscPressed) {
        myEscPressed = false;
        if (affectedDocument != null) {
          // prevent Esc key to leave the document in the not-highlighted state
          if (!myDaemonCodeAnalyzer.getFileStatusMap().allDirtyScopesAreNull(affectedDocument)) {
            stopDaemon(true);
          }
        }
      }
      else if (!myDaemonCodeAnalyzer.isRunning()) {
        stopDaemon(true);
      }
    }
  }

  private class MyEditorColorsListener implements EditorColorsListener {
    public void globalSchemeChange(EditorColorsScheme scheme) {
      myDaemonCodeAnalyzer.restart();
    }
  }

  private class MyTodoListener implements PropertyChangeListener {
    public void propertyChange(PropertyChangeEvent evt) {
      if (TodoConfiguration.PROP_TODO_PATTERNS.equals(evt.getPropertyName())) {
        myDaemonCodeAnalyzer.restart();
      }
    }
  }

  private class MyProfileChangeListener extends ProfileChangeAdapter {
    public void profileChanged(Profile profile) {
      myDaemonCodeAnalyzer.restart();
    }

    public void profileActivated(Profile oldProfile, Profile profile) {
      myDaemonCodeAnalyzer.restart();
    }
  }

  private class MyAnActionListener implements AnActionListener {
    private final AnAction escapeAction = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_ESCAPE);

    public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
      myEscPressed = action == escapeAction;
    }

    public void afterActionPerformed(final AnAction action, final DataContext dataContext, AnActionEvent event) {
    }

    public void beforeEditorTyping(char c, DataContext dataContext) {
      Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
      //no need to stop daemon if something happened in the console
      if (editor != null && !worthBothering(editor.getDocument(), editor.getProject())) {
        return;
      }
      stopDaemon(true);
    }
  }

  private static class MyEditorMouseListener extends EditorMouseAdapter {

    public void mouseExited(EditorMouseEvent e) {
      if (!TooltipController.getInstance().shouldSurvive(e.getMouseEvent())) {
        DaemonTooltipUtil.cancelTooltips();
      }
    }
  }

  private class MyEditorMouseMotionListener implements EditorMouseMotionListener {
    public void mouseMoved(EditorMouseEvent e) {
      Editor editor = e.getEditor();
      if (myProject != editor.getProject()) return;

      boolean shown = false;
      try {
        // There is a possible case that cursor is located at soft wrap-introduced virtual space (that is mapped to offset
        // of the document symbol just after soft wrap). We don't want to show any tooltips for it then.
        VisualPosition visual = editor.xyToVisualPosition(e.getMouseEvent().getPoint());
        if (editor.getSoftWrapModel().isInsideOrBeforeSoftWrap(visual)) {
          return;
        }
        LogicalPosition logical = editor.visualToLogicalPosition(visual);
        if (e.getArea() == EditorMouseEventArea.EDITING_AREA) {
          int offset = editor.logicalPositionToOffset(logical);
          if (editor.offsetToLogicalPosition(offset).column != logical.column) return; // we are in virtual space
          HighlightInfo info = myDaemonCodeAnalyzer.findHighlightByOffset(editor.getDocument(), offset, false);
          if (info == null || info.description == null) return;
          DaemonTooltipUtil.showInfoTooltip(info, editor, offset);
          shown = true;
        }
      }
      finally {
        if (!shown && !TooltipController.getInstance().shouldSurvive(e.getMouseEvent())) {
          DaemonTooltipUtil.cancelTooltips();
        }
      }
    }

    public void mouseDragged(EditorMouseEvent e) {
      TooltipController.getInstance().cancelTooltips();
    }
  }

  private void stopDaemon(boolean toRestartAlarm) {
    myDaemonCodeAnalyzer.stopProcess(toRestartAlarm);
  }

  Collection<FileEditor> getSelectedEditors() {
    // Editors in modal context
    List<Editor> editors = myEditorTracker.getActiveEditors();

    Collection<FileEditor> activeFileEditors = new THashSet<FileEditor>(editors.size());
    for (Editor editor : editors) {
      TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
      activeFileEditors.add(textEditor);
    }
    if (ApplicationManager.getApplication().getCurrentModalityState() != ModalityState.NON_MODAL) {
      return activeFileEditors;
    }

    // Editors in tabs.
    Collection<FileEditor> result = new THashSet<FileEditor>();
    Collection<Document> documents = new THashSet<Document>(activeFileEditors.size());
    final FileEditor[] tabEditors = FileEditorManager.getInstance(myProject).getSelectedEditors();
    for (FileEditor tabEditor : tabEditors) {
      if (tabEditor instanceof TextEditor) {
        documents.add(((TextEditor)tabEditor).getEditor().getDocument());
      }
      result.add(tabEditor);
    }
    // do not duplicate documents
    for (FileEditor fileEditor : activeFileEditors) {
      if (fileEditor instanceof TextEditor && documents.contains(((TextEditor)fileEditor).getEditor().getDocument())) continue;
      result.add(fileEditor);
    }
    return result;
  }
}
