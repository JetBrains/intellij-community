/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PinActiveTabAction extends ToggleAction implements DumbAware {
  /**
   * @return selected editor or <code>null</code>
   */
  @Nullable
  private static VirtualFile getFile(final DataContext context){
    Project project = CommonDataKeys.PROJECT.getData(context);
    if(project == null){
      return null;
    }

    return CommonDataKeys.VIRTUAL_FILE.getData(context);
  }

  /**
   * @return selected content or <code>null</code>
   */
  @Nullable
  private static Content getContent(final DataContext context){
    Content[] contents = ViewContext.CONTENT_KEY.getData(context);
    if (contents != null && contents.length == 1) return contents[0];
    
    ContentManager contentManager = ContentManagerUtil.getContentManagerFromContext(context, true);
    if (contentManager == null){
      return null;
    }
    return contentManager.getSelectedContent();
  }

  @Override
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

  @Override
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
    assert content != null : context;
    content.setPinned(state);
  }

  private static EditorWindow getEditorWindow(DataContext dataContext) {
    EditorWindow editorWindow = EditorWindow.DATA_KEY.getData(dataContext);
    if (editorWindow == null) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project != null) {
        editorWindow = FileEditorManagerEx.getInstanceEx(project).getCurrentWindow();
      }
    }
    return editorWindow;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    Presentation presentation = e.getPresentation();
    DataContext context = e.getDataContext();
    EditorWindow window = getEditorWindow(context);
    if (window == null || window.getOwner().isPreview()) {
      presentation.setEnabledAndVisible(false);
    }
    else {
      if (getFile(context) != null) {
        presentation.setEnabledAndVisible(true);
      }
      else {
        Content content = getContent(context);
        presentation.setEnabledAndVisible(content != null && content.isPinnable());
      }
    }

    if (ActionPlaces.EDITOR_TAB_POPUP.equals(e.getPlace()) || ViewContext.CELL_POPUP_PLACE.equals(e.getPlace())) {
      presentation.setText(isSelected(e) ? IdeBundle.message("action.unpin.tab") : IdeBundle.message("action.pin.tab"));
    }
    else {
      presentation.setText(isSelected(e) ? IdeBundle.message("action.unpin.active.tab") : IdeBundle.message("action.pin.active.tab"));
    }
  }
}
