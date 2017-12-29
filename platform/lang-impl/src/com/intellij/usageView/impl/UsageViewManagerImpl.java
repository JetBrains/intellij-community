/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.usageView.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.*;
import com.intellij.usageView.UsageViewManager;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.UsageViewImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;

public class UsageViewManagerImpl extends UsageViewManager {
  private final Key<Boolean> REUSABLE_CONTENT_KEY = Key.create("UsageTreeManager.REUSABLE_CONTENT_KEY");
  private final Key<Boolean> NOT_REUSABLE_CONTENT_KEY = Key.create("UsageTreeManager.NOT_REUSABLE_CONTENT_KEY");        //todo[myakovlev] dont use it
  private final Key<UsageView> NEW_USAGE_VIEW_KEY = Key.create("NEW_USAGE_VIEW_KEY");
  private final ContentManager myFindContentManager;

  public UsageViewManagerImpl(final Project project, final ToolWindowManager toolWindowManager) {
    ToolWindow toolWindow = toolWindowManager.registerToolWindow(ToolWindowId.FIND, true, ToolWindowAnchor.BOTTOM, project, true);
    toolWindow.setHelpId(UsageViewImpl.HELP_ID);
    toolWindow.setToHideOnEmptyContent(true);
    toolWindow.setIcon(AllIcons.Toolwindows.ToolWindowFind);

    myFindContentManager = toolWindow.getContentManager();
    myFindContentManager.addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void contentRemoved(ContentManagerEvent event) {
        event.getContent().release();
      }
    });
    new ContentManagerWatcher(toolWindow, myFindContentManager);
  }

  @NotNull
  @Override
  public Content addContent(String contentName, boolean reusable, final JComponent component, boolean toOpenInNewTab, boolean isLockable) {
    return addContent(contentName, null, null, reusable, component, toOpenInNewTab, isLockable);
  }

  @NotNull
  @Override
  public Content addContent(String contentName, String tabName, String toolwindowTitle, boolean reusable, final JComponent component,
                            boolean toOpenInNewTab, boolean isLockable) {
    Key<Boolean> contentKey = reusable ? REUSABLE_CONTENT_KEY : NOT_REUSABLE_CONTENT_KEY;

    Content contentToDelete = null;
    if (!toOpenInNewTab && reusable) {
      Content[] contents = myFindContentManager.getContents();

      for (Content content : contents) {
        if (!content.isPinned() &&
            content.getUserData(contentKey) != null
          ) {
          UsageView usageView = content.getUserData(NEW_USAGE_VIEW_KEY);
          if (usageView == null || !usageView.isSearchInProgress()) {
            contentToDelete = content;
          }
        }
      }
    }
    Content content = ContentFactory.SERVICE.getInstance().createContent(component, contentName, isLockable);
    content.setTabName(tabName);
    content.setToolwindowTitle(toolwindowTitle);
    content.putUserData(contentKey, Boolean.TRUE);
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);

    myFindContentManager.addContent(content);
    if (contentToDelete != null) {
      myFindContentManager.removeContent(contentToDelete, true);
    }
    myFindContentManager.setSelectedContent(content);

    return content;
  }

  @Override
  public int getReusableContentsCount() {
    return getContentCount(true);
  }

  private int getContentCount(boolean reusable) {
    Key<Boolean> contentKey = reusable ? REUSABLE_CONTENT_KEY : NOT_REUSABLE_CONTENT_KEY;
    Content[] contents = myFindContentManager.getContents();
    return (int)Arrays.stream(contents).filter(content -> content.getUserData(contentKey) != null).count();
  }

  @Override
  public Content getSelectedContent(boolean reusable) {
    Key<Boolean> contentKey = reusable ? REUSABLE_CONTENT_KEY : NOT_REUSABLE_CONTENT_KEY;
    Content selectedContent = myFindContentManager.getSelectedContent();
    return selectedContent == null || selectedContent.getUserData(contentKey) == null ? null : selectedContent;
  }

  @Override
  public Content getSelectedContent() {
    return myFindContentManager == null ? null : myFindContentManager.getSelectedContent();
  }

  @Override
  public void closeContent(@NotNull Content content) {
    myFindContentManager.removeContent(content, true);
    content.release();
  }
}
