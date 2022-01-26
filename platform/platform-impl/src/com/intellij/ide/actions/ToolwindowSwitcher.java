// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.toolWindow.ToolWindowEventSource;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
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
    invokePopup(project, new ToolWindowsComparator(toolWindowManager.getRecentToolWindows()), null, null);
  }

  public static void invokePopup(Project project,
                                 @NotNull Comparator<? super ToolWindow> comparator,
                                 @Nullable Predicate<? super ToolWindow> filter,
                                 @Nullable RelativePoint point) {
    if (filter == null) {
      filter = window -> true;
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
    IPopupChooserBuilder<ToolWindow> builder = JBPopupFactory.getInstance().createPopupChooserBuilder(toolWindows);
    ToolWindow selected =
      toolWindowManager.getActiveToolWindowId() == null || toolWindows.size() == 1 ? toolWindows.get(0) : toolWindows.get(1);

    popup = builder
      .setRenderer(new ToolWindowsWidgetCellRenderer())
      .setAutoselectOnMouseMove(true)
      .setRequestFocus(true)
      .setSelectedValue(selected, false)
      .setMinSize(new Dimension(300, -1))
      .setNamerForFiltering(x -> x.getStripeTitle())
      .setItemChosenCallback((selectedValue) -> {
        if (popup != null) {
          popup.closeOk(null);
        }
        toolWindowManager.activateToolWindow(selectedValue.getId(), null, true, ToolWindowEventSource.ToolWindowSwitcher);
      }).createPopup();

    Disposer.register(popup, () -> popup = null);
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

  private static final class ToolWindowsWidgetCellRenderer implements ListCellRenderer<ToolWindow> {
    private final JPanel myPanel;
    private final SimpleColoredComponent myTextLabel = new SimpleColoredComponent();
    private final JLabel myShortcutLabel = new JLabel();

    private ToolWindowsWidgetCellRenderer() {
      myPanel = new BorderLayoutPanel() {
        @Override
        public void paintComponent(Graphics g) {
          Color bg = UIUtil.getListBackground(false, false);
          g.setColor(bg);
          g.fillRect(0,0, getWidth(), getHeight());
          if (!getBackground().equals(bg)) {
            g.setColor(getBackground());
            GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
            g.fillRoundRect(4, 1, getWidth() - 8, getHeight() - 2, 8, 8);
            config.restore();
          }
        }
      }.addToLeft(myTextLabel).addToRight(myShortcutLabel);
      myShortcutLabel.setBorder(JBUI.Borders.empty(0, JBUIScale.scale(8), 1, 0));
      myPanel.setBorder(JBUI.Borders.empty(4, 12));
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends ToolWindow> list,
                                                  ToolWindow value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      UIUtil.setBackgroundRecursively(myPanel, UIUtil.getListBackground(isSelected, true));
      myTextLabel.clear();
      myTextLabel.append(value.getStripeTitle());
      Icon icon = value.getIcon();
      if (icon instanceof ScalableIcon) {
        icon = ((ScalableIcon)icon).scale(16f / icon.getIconWidth());
      }
      myTextLabel.setIcon(ObjectUtils.notNull(icon, EmptyIcon.ICON_16));
      myTextLabel.setForeground(UIUtil.getListForeground(isSelected, true));
      myTextLabel.setBackground(UIUtil.getListBackground(isSelected, true));
      String activateActionId = ActivateToolWindowAction.getActionIdForToolWindow(value.getId());
      KeyboardShortcut shortcut = ActionManager.getInstance().getKeyboardShortcut(activateActionId);
      if (shortcut != null) {
        myShortcutLabel.setText(KeymapUtil.getShortcutText(shortcut));
      }
      else {
        myShortcutLabel.setText("");
      }
      myShortcutLabel.setForeground(isSelected ? UIManager.getColor("MenuItem.acceleratorSelectionForeground") : UIManager.getColor("MenuItem.acceleratorForeground"));
      return myPanel;
    }
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
}
