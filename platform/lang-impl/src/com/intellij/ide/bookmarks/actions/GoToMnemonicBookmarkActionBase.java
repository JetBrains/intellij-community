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
package com.intellij.ide.bookmarks.actions;

import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public abstract class GoToMnemonicBookmarkActionBase extends AnAction implements DumbAware {
  private final int myNumber;

  public GoToMnemonicBookmarkActionBase(int n) {
    myNumber = n;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getProject() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      Bookmark bookmark = BookmarkManager.getInstance(project).findBookmarkForMnemonic((char)('0' + myNumber));
      if (bookmark != null) {
        hideBookmarkPopupIfNeed(e);
        bookmark.navigate(true);
      }
    }
  }

  protected void hideBookmarkPopupIfNeed(@NotNull AnActionEvent e) {
    for (Component c = PlatformDataKeys.CONTEXT_COMPONENT.getData(e.getDataContext());
         c != null;
         c = c.getParent()) {
      Object o = c instanceof JComponent ? ((JComponent)c).getClientProperty(BookmarksAction.BOOKMARKS_POPUP_DISPOSER) : null;
      if (o instanceof Disposable) {
        Disposer.dispose((Disposable)o);
        return;
      }
    }
  }

  public static class GotoBookmark0Action extends GoToMnemonicBookmarkActionBase {
    public GotoBookmark0Action() {
      super(0);
    }
  }

  public static class GotoBookmark1Action extends GoToMnemonicBookmarkActionBase {
    public GotoBookmark1Action() {
      super(1);
    }
  }

  public static class GotoBookmark2Action extends GoToMnemonicBookmarkActionBase {
    public GotoBookmark2Action() {
      super(2);
    }
  }

  public static class GotoBookmark3Action extends GoToMnemonicBookmarkActionBase {
    public GotoBookmark3Action() {
      super(3);
    }
  }

  public static class GotoBookmark4Action extends GoToMnemonicBookmarkActionBase {
    public GotoBookmark4Action() {
      super(4);
    }
  }

  public static class GotoBookmark5Action extends GoToMnemonicBookmarkActionBase {
    public GotoBookmark5Action() {
      super(5);
    }
  }

  public static class GotoBookmark6Action extends GoToMnemonicBookmarkActionBase {
    public GotoBookmark6Action() {
      super(6);
    }
  }

  public static class GotoBookmark7Action extends GoToMnemonicBookmarkActionBase {
    public GotoBookmark7Action() {
      super(7);
    }
  }

  public static class GotoBookmark8Action extends GoToMnemonicBookmarkActionBase {
    public GotoBookmark8Action() {
      super(8);
    }
  }

  public static class GotoBookmark9Action extends GoToMnemonicBookmarkActionBase {
    public GotoBookmark9Action() {
      super(9);
    }
  }
}
