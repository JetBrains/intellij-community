package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerUtil;

public class PinActiveTabAction extends ToggleAction {
  /**
   * @return selected editor or <code>null</code>
   */
  private VirtualFile getFile(final DataContext context){
    Project project = DataKeys.PROJECT.getData(context);
    if(project == null){
      return null;
    }

    // To provide file from editor manager, editor component should be active
    if(!ToolWindowManager.getInstance(project).isEditorComponentActive()){
      return null;
    }

    return DataKeys.VIRTUAL_FILE.getData(context);
  }

  /**
   * @return selected content or <code>null</code>
   */
  private Content getContent(final DataContext context){
    ContentManager contentManager = ContentManagerUtil.getContentManagerFromContext(context, true);
    if (contentManager == null){
      return null;
    }
    return contentManager.getSelectedContent();
  }

  public boolean isSelected(AnActionEvent e) {
    DataContext context = e.getDataContext();
    VirtualFile file = getFile(context);
    if(file != null){
      // 1. Check editor
      EditorWindow editorWindow = getEditorWindow(context);
      if (editorWindow != null) {
        if (!editorWindow.isFileOpen(file)) {
          file = editorWindow.getSelectedFile();
          if (file == null) return false;
        }

        return editorWindow.isFilePinned(file);
      }
    }
    // 2. Check content
    final Content content = getContent(context);
    if(content != null){
      return content.isPinned();
    }
    else{
      return false;
    }
  }

  public void setSelected(AnActionEvent e, boolean state) {
    DataContext context = e.getDataContext();
    VirtualFile file = getFile(context);
    if(file != null){
      // 1. Check editor
      EditorWindow editorWindow = getEditorWindow(context);
      if (editorWindow != null) {
        if (!editorWindow.isFileOpen(file)) {
          file = editorWindow.getSelectedFile();
          if (file == null) return;
        }

        editorWindow.setFilePinned(file, state);
        return;
      }
    }
    Content content = getContent(context); // at this point content cannot be null
    content.setPinned(state);
  }

  private EditorWindow getEditorWindow(DataContext context) {
    final Project project = DataKeys.PROJECT.getData(context);
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    EditorWindow editorWindow = (EditorWindow) context.getData(DataConstantsEx.EDITOR_WINDOW);
    if (editorWindow == null) {
      editorWindow = fileEditorManager.getCurrentWindow();
    }
    return editorWindow;
  }

  public void update(AnActionEvent e){
    super.update(e);
    Presentation presentation = e.getPresentation();
    DataContext context = e.getDataContext();
    presentation.setEnabled(getFile(context) != null || getContent(context) != null);
    if (ActionPlaces.EDITOR_TAB_POPUP.equals(e.getPlace())) {
      presentation.setText(isSelected(e) ? IdeBundle.message("action.unpin.tab") : IdeBundle.message("action.pin.tab"));
    } else {
      presentation.setText(isSelected(e) ? IdeBundle.message("action.unpin.active.tab") : IdeBundle.message("action.pin.active.tab"));
    }
  }
}
