// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usageView.impl;

import com.intellij.find.FindBundle;
import com.intellij.find.FindUsagesSettings;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCloseListener;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.IdeFrameGuests;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.ui.UIBundle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewContentManager;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewSettings;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.event.HyperlinkListener;
import java.util.Arrays;
import java.util.List;

/**
 * The tabs of the Find tool window of a project. A frame guest puts its tabs into the Find tool window of its frame owner,
 * the only one its frame shows, see {@link IdeFrameGuests#frameOwnerOf}: there the guest and the owner see only their own
 * tabs as selected, and the guest takes its tabs away when it closes. A guest stays one for its whole life.
 */
public final class UsageViewContentManagerImpl extends UsageViewContentManager implements Disposable {
  /** The manager that added a tab, so that the managers sharing one tool window keep to their own tabs. */
  private static final Key<UsageViewContentManagerImpl> MANAGER_KEY = Key.create("UsageViewContentManagerImpl.MANAGER_KEY");

  private final Key<Boolean> REUSABLE_CONTENT_KEY = Key.create("UsageTreeManager.REUSABLE_CONTENT_KEY");
  //todo[myakovlev] dont use it
  private final Key<Boolean> NOT_REUSABLE_CONTENT_KEY = Key.create("UsageTreeManager.NOT_REUSABLE_CONTENT_KEY");
  private final Key<UsageView> NEW_USAGE_VIEW_KEY = Key.create("NEW_USAGE_VIEW_KEY");
  private final boolean myFrameGuest;
  private final @NotNull ToolWindow myToolWindow;
  private final @NotNull ContentManager myFindContentManager;
  /** The balloon that a frame guest shows in the tool window of its frame owner, while it is shown. */
  private @Nullable Balloon myBalloon;

  @SuppressWarnings("unused") // instantiated reflectively
  @RequiresEdt
  public UsageViewContentManagerImpl(@NotNull Project project) {
    this(project, ToolWindowManager.getInstance(project));
  }

  @RequiresEdt
  @NonInjectable
  public UsageViewContentManagerImpl(@NotNull Project project,
                                     @NotNull ToolWindowManager toolWindowManager) {
    myFrameGuest = IdeFrameGuests.isFrameGuest(project);
    // a guest shows its tabs in the Find tool window that its frame owner registers and configures, with the gear actions for both
    ToolWindow toolWindow = myFrameGuest
                            ? getInstance(IdeFrameGuests.frameOwnerOf(project)).getToolWindow()
                            : getOrRegisterFindToolWindow(toolWindowManager);
    myToolWindow = toolWindow;
    myFindContentManager = toolWindow.getContentManager();
    if (myFrameGuest) {
      // before the write action that disposes the guest: removing the last tab can hide a floating tool window
      ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
        @Override
        public void projectClosing(@NotNull Project closing) {
          if (closing == project) removeOwnContents();
        }
      });
      return;
    }
    toolWindow.setHelpId(UsageViewImpl.HELP_ID);
    toolWindow.setToHideOnEmptyContent(true);

    DumbAwareToggleAction toggleNewTabAction = new DumbAwareToggleAction(FindBundle.message("find.open.in.new.tab.action")) {
      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return FindUsagesSettings.getInstance().isShowResultsInSeparateView();
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        FindUsagesSettings.getInstance().setShowResultsInSeparateView(state);
      }
    };

    DumbAwareToggleAction toggleSortAction =
      new DumbAwareToggleAction(UsageViewBundle.message("sort.alphabetically.action.text"), null, AllIcons.ObjectBrowser.Sorted) {
        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
          return UsageViewSettings.getInstance().isSortAlphabetically();
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.BGT;
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
          UsageViewSettings.getInstance().setSortAlphabetically(state);
          project.getMessageBus().syncPublisher(UsageFilteringRuleProvider.RULES_CHANGED).run();
          for (Project guest : IdeFrameGuests.frameGuestsOf(project)) {
            guest.getMessageBus().syncPublisher(UsageFilteringRuleProvider.RULES_CHANGED).run();
          }
        }
      };

    DumbAwareToggleAction toggleAutoscrollAction = new DumbAwareToggleAction(UIBundle.message("autoscroll.to.source.action.name"),
                                                                             UIBundle.message("autoscroll.to.source.action.description"),
                                                                             AllIcons.General.AutoscrollToSource) {
      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return UsageViewSettings.getInstance().isAutoScrollToSource();
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        UsageViewSettings.getInstance().setAutoScrollToSource(state);
      }
    };

    DefaultActionGroup gearActions = DefaultActionGroup.createPopupGroup(IdeBundle.messagePointer("group.view.options"));
    gearActions.addAll(toggleAutoscrollAction, toggleSortAction, toggleNewTabAction);
    toolWindow.setAdditionalGearActions(gearActions);
  }

  private static @NotNull ToolWindow getOrRegisterFindToolWindow(@NotNull ToolWindowManager toolWindowManager) {
    if (toolWindowManager.getToolWindowIdSet().contains(ToolWindowId.FIND)) {
      ToolWindow toolWindow = toolWindowManager.getToolWindow(ToolWindowId.FIND);
      if (toolWindow != null) return toolWindow;
      else //noinspection deprecation
        toolWindowManager.unregisterToolWindow(ToolWindowId.FIND);
    }
    return toolWindowManager.registerToolWindow(
      ToolWindowId.FIND,
      builder -> {
        builder.stripeTitle = UIBundle.messagePointer("tool.window.name.find");
        builder.icon = AllIcons.Toolwindows.ToolWindowFind;
        builder.shouldBeAvailable = false;
        return Unit.INSTANCE;
      }
    );
  }

  @Override
  public @NotNull Content addContent(@NotNull String contentName,
                                     boolean reusable,
                                     final @NotNull JComponent component,
                                     boolean toOpenInNewTab,
                                     boolean isLockable) {
    return addContent(contentName, null, null, reusable, component, toOpenInNewTab, isLockable);
  }

  @Override
  public @NotNull Content addContent(@NotNull String contentName,
                                     String tabName,
                                     String toolwindowTitle,
                                     boolean reusable,
                                     final @NotNull JComponent component,
                                     boolean toOpenInNewTab,
                                     boolean isLockable) {
    Key<Boolean> contentKey = reusable ? REUSABLE_CONTENT_KEY : NOT_REUSABLE_CONTENT_KEY;
    Content selectedContent = getSelectedContent();
    toOpenInNewTab |= selectedContent != null && selectedContent.isPinned();

    Content contentToDelete = null;
    int indexToAdd = -1;
    if (!toOpenInNewTab && reusable) {
      List<Content> contents = Arrays.asList(myFindContentManager.getContents());
      if (selectedContent != null) {
        contents = ContainerUtil.append(contents, selectedContent);
        // Selected content has to be the last (and the best) candidate to be deleted
      }

      for (Content content : contents) {
        if (!content.isPinned() && content.getUserData(contentKey) != null) {
          UsageView usageView = content.getUserData(NEW_USAGE_VIEW_KEY);
          if (usageView == null || !usageView.isSearchInProgress()) {
            contentToDelete = content;
            indexToAdd = myFindContentManager.getIndexOfContent(contentToDelete);
          }
        }
      }
    }
    Content content = ContentFactory.getInstance().createContent(component, contentName, isLockable);
    content.setTabName(tabName);
    content.setToolwindowTitle(toolwindowTitle);
    content.putUserData(contentKey, Boolean.TRUE);
    content.putUserData(MANAGER_KEY, this);
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);

    myFindContentManager.addContent(content, indexToAdd);
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

  /** The selected tab, unless another manager sharing the tool window added it. */
  @Override
  public Content getSelectedContent() {
    Content selectedContent = myFindContentManager.getSelectedContent();
    if (selectedContent == null) return null;
    UsageViewContentManagerImpl manager = selectedContent.getUserData(MANAGER_KEY);
    return manager == null || manager == this ? selectedContent : null;
  }

  @Override
  public void closeContent(@NotNull Content content) {
    myFindContentManager.removeContent(content, true);
    content.release();
  }

  @Override
  public @NotNull ToolWindow getToolWindow() {
    return myToolWindow;
  }

  @Override
  public void notifyByBalloon(@NotNull MessageType type,
                              @NlsContexts.PopupContent @NotNull String html,
                              @Nullable Icon icon,
                              @Nullable HyperlinkListener listener) {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myToolWindow.getProject());
    //noinspection SSBasedInspection
    toolWindowManager.notifyByBalloon(myToolWindow.getId(), type, html, icon, listener);
    if (!myFrameGuest) return;
    Balloon balloon = toolWindowManager.getToolWindowBalloon(myToolWindow.getId());
    if (balloon == null) return;
    myBalloon = balloon;
    Disposer.register(balloon, () -> {
      if (myBalloon == balloon) myBalloon = null;
    });
  }

  /** A guest that closes without a closing event, such as one that never opened, takes its tabs away here. */
  @Override
  public void dispose() {
    if (myFrameGuest) removeOwnContents();
  }

  /** A guest takes its tabs and its balloon away from the tool window of its frame owner; those of an owner go with its tool window. */
  private void removeOwnContents() {
    Balloon balloon = myBalloon;
    if (balloon != null) balloon.hide();
    if (myFindContentManager.isDisposed()) return;
    for (Content content : myFindContentManager.getContents()) {
      if (content.getUserData(MANAGER_KEY) == this) {
        myFindContentManager.removeContent(content, true);
      }
    }
  }
}
