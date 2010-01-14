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
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerUtil;
import org.jetbrains.annotations.Nullable;

public class PinActiveTabAction extends ToggleAction implements DumbAware {
  /**
   * @return selected editor or <code>null</code>
   */
  @Nullable
  private static VirtualFile getFile(final DataContext context){
    Project project = PlatformDataKeys.PROJECT.getData(context);
    if(project == null){
      return null;
    }

    // To provide file from editor manager, editor component should be active
    if(!ToolWindowManager.getInstance(project).isEditorComponentActive()){
      return null;
    }

    return PlatformDataKeys.VIRTUAL_FILE.getData(context);
  }

  /**
   * @return selected content or <code>null</code>
   */
  @Nullable
  private static Content getContent(final DataContext context){
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

  private static EditorWindow getEditorWindow(DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    EditorWindow editorWindow = EditorWindow.DATA_KEY.getData(dataContext);
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
