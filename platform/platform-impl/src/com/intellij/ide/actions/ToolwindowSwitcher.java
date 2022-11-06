// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.toolWindow.ToolWindowEventSource;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.util.PopupImplUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author Konstantin Bulenkov
 */
public final class ToolwindowSwitcher extends DumbAwareAction {
  private static JBPopup popup;

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;
    ToolWindowManagerImpl toolWindowManager = (ToolWindowManagerImpl)ToolWindowManager.getInstance(project);
    invokePopup(project, new ToolWindowsComparator(toolWindowManager.getRecentToolWindows()), e.getDataContext(), null, null, null);
  }

  public static void invokePopup(Project project,
                                 @NotNull Comparator<? super ToolWindow> comparator,
                                 @NotNull DataContext dataContext,
                                 @Nullable Predicate<? super ToolWindow> filter,
                                 @Nullable RelativePoint point,
                                 @Nullable Component invokingButton) {
    if (filter == null) {
      filter = Predicates.alwaysTrue();
    }

    if (popup != null) {
      gotoNextElement(popup);
      return;
    }

    ToolWindowManagerImpl toolWindowManager = (ToolWindowManagerImpl)ToolWindowManager.getInstance(project);
    List<ToolWindow> toolWindows = getToolWindows(toolWindowManager, filter);
    if (toolWindows.isEmpty()) {
      return;
    }

    toolWindows.sort(comparator);

    List<ToolWindowAction> actions = ContainerUtil.map(toolWindows, it -> new ToolWindowAction(project, it));
    popup = JBPopupFactory.getInstance().createActionGroupPopup(null, new DefaultActionGroup(actions), dataContext, null, true);
    popup.setMinimumSize(new Dimension(300, -1));

    Disposer.register(popup, () -> popup = null);
    PopupImplUtil.setPopupToggleButton(popup, invokingButton);
    if (point == null) {
      popup.showCenteredInCurrentWindow(project);
    }
    else {
      popup.show(point);
    }
  }

   public static @NotNull List<ToolWindow> getToolWindows(@NotNull ToolWindowManagerEx toolWindowManager,
                                                          @NotNull Predicate<? super ToolWindow> filter) {
     List<ToolWindow> list = new ArrayList<>();
     for (ToolWindow toolWindow : toolWindowManager.getToolWindows()) {
       if (toolWindow != null && toolWindow.isAvailable() && filter.test(toolWindow)) {
         list.add(toolWindow);
       }
     }
     return list;
  }

  private static void gotoNextElement(JBPopup popup) {
    JList<?> list = UIUtil.findComponentOfType(popup.getContent(), JList.class);
    if (list != null) {
      ScrollingUtil.moveDown(list, 0);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static final class ToolWindowsComparator implements Comparator<ToolWindow> {
    private final List<String> myRecent;

    private ToolWindowsComparator(List<String> recent) {
      myRecent = recent;
    }

    @Override
    public int compare(ToolWindow o1, ToolWindow o2) {
      int index1 = myRecent.indexOf(o1.getId());
      int index2 = myRecent.indexOf(o2.getId());
      if (index1 >= 0 && index2 >= 0) {
        return index1 - index2;
      }

      if (index1 >= 0) return -1;
      if (index2 >= 0) return  1;

      return NaturalComparator.INSTANCE.compare(o1.getStripeTitle(), o2.getStripeTitle());
    }
  }

  private static class ToolWindowAction extends DumbAwareAction {

    @NotNull
    private final Project project;

    @NotNull
    private final ToolWindow toolWindow;

    private ToolWindowAction(@NotNull Project project, @NotNull ToolWindow toolWindow) {
      super(toolWindow.getStripeTitle(), null, getIcon(toolWindow));
      this.project = project;
      this.toolWindow = toolWindow;

      String activateActionId = ActivateToolWindowAction.getActionIdForToolWindow(toolWindow.getId());
      KeyboardShortcut shortcut = ActionManager.getInstance().getKeyboardShortcut(activateActionId);
      if (shortcut != null) {
        setShortcutSet(new CustomShortcutSet(shortcut));
      }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (popup != null) {
        popup.closeOk(null);
      }
      ToolWindowManagerImpl toolWindowManager = (ToolWindowManagerImpl)ToolWindowManager.getInstance(project);
      toolWindowManager.activateToolWindow(toolWindow.getId(), null, true, ToolWindowEventSource.ToolWindowSwitcher);
    }

    private static Icon getIcon(@NotNull ToolWindow toolWindow) {
      Icon icon = toolWindow.getIcon();
      if (icon instanceof ScalableIcon) {
        icon = ((ScalableIcon)icon).scaleToWidth(JBUIScale.scale(16f));
      }
      return ObjectUtils.notNull(icon, EmptyIcon.ICON_16);
    }
  }

}
