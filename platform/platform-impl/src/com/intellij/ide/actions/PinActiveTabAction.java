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
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
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

  @Override
  public boolean isSelected(AnActionEvent e) {
    Content content = getNonEditorContent(e);
    if (content != null && content.isPinnable()) return content.isPinned();

    EditorWindow window = getEditorWindow(e);
    VirtualFile selectedFile = window == null ? null : getFileInWindow(e, window);
    return selectedFile != null && window.isFilePinned(selectedFile);
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    Content content = getNonEditorContent(e);
    if (content != null && content.isPinnable()) {
      content.setPinned(state);
    }
    else {
      EditorWindow window = getEditorWindow(e);
      VirtualFile selectedFile = window == null ? null : getFileInWindow(e, window);
      if (selectedFile != null) {
        window.setFilePinned(selectedFile, state);
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean selected = isSelected(e);
    e.getPresentation().putClientProperty(SELECTED_PROPERTY, selected);

    String text;
    boolean enable;
    EditorWindow window = getEditorWindow(e);
    VirtualFile selectedFile = window == null ? null : getFileInWindow(e, window);
    if (selectedFile != null) {
      enable = !window.getOwner().isPreview();
    }
    else {
      Content content = getNonEditorContent(e);
      enable = content != null && content.isPinnable();
    }
    // add the word "active" if the target tab is not current
    if (ActionPlaces.isMainMenuOrActionSearch(e.getPlace()) ||
        !(selectedFile == null || selectedFile.equals(e.getData(CommonDataKeys.VIRTUAL_FILE)))) {
      text = selected ? IdeBundle.message("action.unpin.active.tab") : IdeBundle.message("action.pin.active.tab");
    }
    else {
      text = selected ? IdeBundle.message("action.unpin.tab") : IdeBundle.message("action.pin.tab");
    }
    e.getPresentation().setIcon(ActionPlaces.isToolbarPlace(e.getPlace())? AllIcons.General.Pin_tab : null);
    e.getPresentation().setText(text);
    e.getPresentation().setEnabledAndVisible(enable);
  }

  @Nullable
  private static Content getNonEditorContent(@NotNull AnActionEvent e) {
    if (e.getData(EditorWindow.DATA_KEY) != null) return null;
    Content[] contents = e.getData(ViewContext.CONTENT_KEY);
    if (contents != null && contents.length == 1) return contents[0];

    ContentManager contentManager = ContentManagerUtil.getContentManagerFromContext(e.getDataContext(), true);
    return contentManager == null ? null : contentManager.getSelectedContent();
  }

  @Nullable
  private static EditorWindow getEditorWindow(@NotNull AnActionEvent e) {
    EditorWindow window = e.getData(EditorWindow.DATA_KEY);
    if (window == null) {
      Project project = e.getProject();
      window = project == null ? null : FileEditorManagerEx.getInstanceEx(project).getCurrentWindow();
    }
    return window;
  }

  @Nullable
  private static VirtualFile getFileInWindow(@NotNull AnActionEvent e, @NotNull EditorWindow window) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (file == null) file = window.getSelectedFile();
    if (file != null && window.isFileOpen(file)) return file;
    return null;
  }
}
