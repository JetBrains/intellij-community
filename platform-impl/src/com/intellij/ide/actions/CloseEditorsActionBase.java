package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.ide.IdeBundle;

import java.util.ArrayList;

/**
 * @author yole
 */
public abstract class CloseEditorsActionBase extends AnAction {
  private ArrayList<Pair<EditorComposite, EditorWindow>> getFilesToClose (final AnActionEvent event) {
    final ArrayList<Pair<EditorComposite, EditorWindow>> res = new ArrayList<Pair<EditorComposite, EditorWindow>>();
    final DataContext dataContext = event.getDataContext();
    final Project project = event.getData(PlatformDataKeys.PROJECT);
    final FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(project);
    final EditorWindow editorWindow = (EditorWindow)dataContext.getData(DataConstantsEx.EDITOR_WINDOW);
    final EditorWindow[] windows;
    if (editorWindow != null){
      windows = new EditorWindow[]{ editorWindow };
    }
    else {
      windows = editorManager.getWindows ();
    }
    final FileStatusManager fileStatusManager = FileStatusManager.getInstance(project);
    if (fileStatusManager != null) {
      for (int i = 0; i != windows.length; ++ i) {
        final EditorWindow window = windows [i];
        final EditorComposite [] editors = window.getEditors ();
        for (final EditorComposite editor : editors) {
          if (isFileToClose(editor, window)) {
            res.add(Pair.create(editor, window));
          }
        }
      }
    }
    return res;
  }

  protected abstract boolean isFileToClose(EditorComposite editor, EditorWindow window);

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(
      project, new Runnable(){
        public void run() {
          final ArrayList<Pair<EditorComposite, EditorWindow>> filesToClose = getFilesToClose (e);
          for (int i = 0; i != filesToClose.size (); ++ i) {
            final Pair<EditorComposite, EditorWindow> we = filesToClose.get(i);
            we.getSecond ().closeFile (we.getFirst ().getFile ());
          }
        }
      }, IdeBundle.message("command.close.all.unmodified.editors"), null
    );
  }

  public void update(final AnActionEvent event){
    final Presentation presentation = event.getPresentation();
    final DataContext dataContext = event.getDataContext();
    final EditorWindow editorWindow = (EditorWindow)dataContext.getData(DataConstantsEx.EDITOR_WINDOW);
    final boolean inSplitter = editorWindow != null && editorWindow.inSplitter();
    presentation.setText(getPresentationText(inSplitter));
    final Project project = event.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(getFilesToClose (event).size () > 0 && isValidForProject(project));
  }

  protected abstract boolean isValidForProject(Project project);

  protected abstract String getPresentationText(boolean inSplitter);
}
