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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Pins any kind of tab in context: editor tab, toolwindow tab or other tabs.
 *
 * todo drop TW and EW, pro: one action-one shortcut; contra: menu|Window changes
 */
public class PinActiveTabAction extends ToggleAction implements DumbAware {

  public static abstract class Handler {
    public final boolean isPinned;
    public final boolean isActiveTab;

    abstract void setPinned(boolean value);

    public Handler(boolean isPinned, boolean isActiveTab) {
      this.isPinned = isPinned;
      this.isActiveTab = isActiveTab;
    }
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    Handler handler = getHandler(e);
    return handler != null && handler.isPinned;
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    Handler handler = getHandler(e);
    if (handler != null) handler.setPinned(state);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Handler handler = getHandler(e);
    boolean enabled = handler != null;
    boolean selected = enabled && handler.isPinned;

    e.getPresentation().setIcon(ActionPlaces.isToolbarPlace(e.getPlace()) ? AllIcons.General.Pin_tab : null);
    e.getPresentation().putClientProperty(SELECTED_PROPERTY, selected);

    String text;
    // add the word "active" if the target tab is not current
    if (ActionPlaces.isMainMenuOrActionSearch(e.getPlace()) || handler != null && !handler.isActiveTab) {
      text = selected ? IdeBundle.message("action.unpin.active.tab") : IdeBundle.message("action.pin.active.tab");
    }
    else {
      text = selected ? IdeBundle.message("action.unpin.tab") : IdeBundle.message("action.pin.tab");
    }
    e.getPresentation().setText(text);
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  protected Handler getHandler(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    EditorWindow currentWindow = e.getData(EditorWindow.DATA_KEY);

    Content content = currentWindow != null ? null : getContentFromEvent(e);
    if (content != null && content.isPinnable()) {
      return new Handler(content.isPinned(), content.getManager().getSelectedContent() == content) {
        @Override
        void setPinned(boolean value) {
          content.setPinned(value);
        }
      };
    }

    final EditorWindow window = currentWindow != null ? currentWindow :
                                project != null ? FileEditorManagerEx.getInstanceEx(project).getCurrentWindow() : null;
    VirtualFile selectedFile = window == null ? null : getFileFromEvent(e, window);
    if (selectedFile != null) {
      return new Handler(window.isFilePinned(selectedFile), selectedFile.equals(e.getData(CommonDataKeys.VIRTUAL_FILE))) {
        @Override
        void setPinned(boolean value) {
          window.setFilePinned(selectedFile, value);
        }
      };
    }
    return null;
  }

  @Nullable
  protected VirtualFile getFileFromEvent(@NotNull AnActionEvent e, @NotNull EditorWindow window) {
    return getFileInWindow(e, window);
  }

  @Nullable
  protected Content getContentFromEvent(@NotNull AnActionEvent e) {
    Content content = getNonToolWindowContent(e);
    return content != null ? content : getToolWindowContent(e);
  }

  @Nullable
  private static Content getNonToolWindowContent(@NotNull AnActionEvent e) {
    Content result = null;
    Content[] contents = e.getData(ViewContext.CONTENT_KEY);
    if (contents != null && contents.length == 1) result = contents[0];
    if (result != null && result.isPinnable()) return result;

    ContentManager contentManager = ContentManagerUtil.getContentManagerFromContext(e.getDataContext(), true);
    result = contentManager != null? contentManager.getSelectedContent() : null;
    if (result != null && result.isPinnable()) return result;
    return getToolWindowContent(e);
  }

  @Nullable
  private static Content getToolWindowContent(@NotNull AnActionEvent e) {
    // note to future readers: TW tab "pinned" icon is shown when content.getUserData(TW.SHOW_CONTENT_ICON) is true
    ToolWindow window = PlatformDataKeys.TOOL_WINDOW.getData(e.getDataContext());
    Content result = window != null ? window.getContentManager().getSelectedContent() : null;
    return result != null && result.isPinnable() ? result : null;
  }

  @Nullable
  private static VirtualFile getFileInWindow(@NotNull AnActionEvent e, @NotNull EditorWindow window) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (file == null) file = window.getSelectedFile();
    if (file != null && window.isFileOpen(file)) return file;
    return null;
  }

  public static class TW extends PinActiveTabAction {
    @Nullable
    @Override
    protected VirtualFile getFileFromEvent(@NotNull AnActionEvent e, @NotNull EditorWindow window) {
      return null;
    }

    @Override
    protected Content getContentFromEvent(@NotNull AnActionEvent e) {
      return getToolWindowContent(e);
    }
  }

  public static class EW extends PinActiveTabAction {

    @Override
    protected Content getContentFromEvent(@NotNull AnActionEvent e) {
      return null;
    }
  }
}
