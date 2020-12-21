// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowEventSource;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ToolwindowSwitcher extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;
    List<ToolWindow> toolWindows = new ArrayList<>();
    final ToolWindowManagerImpl toolWindowManager = (ToolWindowManagerImpl)ToolWindowManager.getInstance(project);
    for (String id : toolWindowManager.getToolWindowIds()) {
      final ToolWindow tw = toolWindowManager.getToolWindow(id);
      if (tw != null && tw.isAvailable() && tw.isShowStripeButton()) {
        toolWindows.add(tw);
      }
    }

    toolWindows.sort(new ToolWindowsComparator(toolWindowManager.getRecentToolWindows()));
    IPopupChooserBuilder<ToolWindow> builder = JBPopupFactory.getInstance().createPopupChooserBuilder(toolWindows);

    Ref<JBPopup> popup = Ref.create();
    popup.set(builder
                .setRenderer(new ToolWindowsWidgetCellRenderer())
                .setAutoselectOnMouseMove(true)
                .setRequestFocus(true)
                .setMinSize(new Dimension(300, -1))
                .setItemChosenCallback((selectedValue) -> {
                  if (popup.get() != null) {
                    popup.get().closeOk(null);
                  }
                  toolWindowManager.activateToolWindow(selectedValue.getId(), null, true, ToolWindowEventSource.ToolWindowSwitcher);
                }).createPopup());

    popup.get().showCenteredInCurrentWindow(project);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }

  private static class ToolWindowsWidgetCellRenderer implements ListCellRenderer<ToolWindow> {
    private final JPanel myPanel;
    private final JLabel myTextLabel = new JLabel();
    private final JLabel myShortcutLabel = new JLabel();

    private ToolWindowsWidgetCellRenderer() {
      myPanel = JBUI.Panels.simplePanel().addToLeft(myTextLabel).addToRight(myShortcutLabel);
      myShortcutLabel.setBorder(JBUI.Borders.empty(0, JBUIScale.scale(8), 1, 0));
      myPanel.setBorder(JBUI.Borders.empty(2, 10));
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends ToolWindow> list,
                                                  ToolWindow value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      UIUtil.setBackgroundRecursively(myPanel, UIUtil.getListBackground(isSelected, true));
      myTextLabel.setText(value.getStripeTitle());
      myTextLabel.setIcon(value.getIcon());
      myTextLabel.setForeground(UIUtil.getListForeground(isSelected, true));
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

  private static class ToolWindowsComparator implements Comparator<ToolWindow> {
    private final ArrayList<String> myRecent;

    private ToolWindowsComparator(ArrayList<String> recent) {
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

      return StringUtil.naturalCompare(o1.getStripeTitle(), o2.getStripeTitle());
    }
  }
}
