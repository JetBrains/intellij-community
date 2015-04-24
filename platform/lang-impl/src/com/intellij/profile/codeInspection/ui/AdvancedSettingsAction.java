/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionConfigTreeNode;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dmitry Batkovich
 */
public abstract class AdvancedSettingsAction extends DumbAwareAction {
  private final int myCheckBoxIndent;
  private Project myProject;
  private InspectionConfigTreeNode myRoot;

  public AdvancedSettingsAction(final Project project, InspectionConfigTreeNode root) {
    super("Advanced Settings");
    getTemplatePresentation().setIcon(AllIcons.General.Gear);
    myProject = project;
    myRoot = root;
    myCheckBoxIndent = calculateCheckBoxIndent();
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    final InspectionProfileImpl inspectionProfile = getInspectionProfile();
    final Icon icon = AllIcons.General.Gear;
    e.getPresentation().setIcon(
      (inspectionProfile != null && inspectionProfile.isProfileLocked()) ? LayeredIcon.create(icon, PlatformIcons.LOCKED_ICON) : icon);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ListPopupImpl actionGroupPopup = (ListPopupImpl)JBPopupFactory.getInstance().createListPopup(
      new BaseListPopupStep<MyAction>(null, ContainerUtil.list(new MyDisableNewInspectionsAction(), new MyResetAction())) {
        @Override
        public PopupStep onChosen(MyAction selectedValue, boolean finalChoice) {
          if (selectedValue.enabled()) {
            selectedValue.actionPerformed();
          }
          return FINAL_CHOICE;
        }
      });
    actionGroupPopup.getList().setCellRenderer(new ListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        return ((MyAction)value).createCustomComponent(isSelected);
      }
    });
    final Component component = e.getInputEvent().getComponent();
    actionGroupPopup.show(new RelativePoint(component, new Point(component.getWidth() - 1, 0)));
  }

  private JLabel installLeftIndentToLabel(final JLabel label) {
    label.setBorder(BorderFactory.createEmptyBorder(0, myCheckBoxIndent, 0, 0));
    return label;
  }

  private class MyResetAction extends MyAction {

    protected MyResetAction() {
      super("All your changes will be lost");
    }

    @Override
    protected JComponent createBaseComponent() {
      return installLeftIndentToLabel(new JLabel("Reset to Default Settings"));
    }

    @Override
    public void actionPerformed() {
      final InspectionProfileImpl inspectionProfile = getInspectionProfile();
      if (inspectionProfile == null) {
        return;
      }
      inspectionProfile.resetToBase(myProject);
      postProcessModification();
    }

    @Override
    protected boolean enabled() {
      return myRoot.isProperSetting();
    }
  }

  private class MyDisableNewInspectionsAction extends MyAction {
    public MyDisableNewInspectionsAction() {
      super("New inspections may appear when " + ApplicationNamesInfo.getInstance().getFullProductName() + " is updated");
    }

    @Override
    protected JComponent createBaseComponent() {
      final JCheckBox checkBox = new JCheckBox("Disable new inspections by default");
      final InspectionProfileImpl profile = getInspectionProfile();
      checkBox.setEnabled(profile != null);
      if (profile != null) {
        checkBox.setSelected(profile.isProfileLocked());
      }
      checkBox.setOpaque(false);
      return checkBox;
    }

    @Override
    public void actionPerformed() {
      final InspectionProfileImpl profile = getInspectionProfile();
      if (profile != null) {
        profile.lockProfile(!profile.isProfileLocked());
      }
    }


    @Override
    protected boolean enabled() {
      return true;
    }
  }

  private abstract class MyAction {
    private final String myDescription;

    protected MyAction(String description) {
      myDescription = description;
    }

    protected abstract JComponent createBaseComponent();

    protected abstract void actionPerformed();

    protected abstract boolean enabled();

    public JComponent createCustomComponent(final boolean selected) {
      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
      final JComponent baseComponent = createBaseComponent();
      panel.add(baseComponent);
      final JLabel descriptionLabel = new JLabel(myDescription);
      UIUtil.applyStyle(UIUtil.ComponentStyle.MINI, descriptionLabel);
      panel.add(installLeftIndentToLabel(descriptionLabel));
      UIUtil.setEnabled(panel, enabled(), true);

      panel.setBackground(selected ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground());
      descriptionLabel.setForeground(selected ? UIUtil.getListSelectionForeground() : UIUtil.getListForeground());
      baseComponent.setForeground(selected ? UIUtil.getListSelectionForeground() : UIUtil.getListForeground());
      return panel;
    }
  }

  protected abstract InspectionProfileImpl getInspectionProfile();

  protected abstract void postProcessModification();

  private static int calculateCheckBoxIndent() {
    JCheckBox checkBox = new JCheckBox();
    Icon icon = checkBox.getIcon();
    int indent = 0;
    if (icon == null) {
      icon = UIManager.getIcon("CheckBox.icon");
    }
    if (UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) {
      icon = EmptyIcon.create(20, 18);
    }
    if (icon != null) {
      final Rectangle r1 = new Rectangle(checkBox.getWidth(), checkBox.getHeight());
      JBInsets.removeFrom(r1, checkBox.getInsets());
      final Rectangle iconRect = new Rectangle();
      SwingUtilities.layoutCompoundLabel(
        checkBox, checkBox.getFontMetrics(checkBox.getFont()), checkBox.getText(), icon,
        checkBox.getVerticalAlignment(), checkBox.getHorizontalAlignment(),
        checkBox.getVerticalTextPosition(), checkBox.getHorizontalTextPosition(),
        r1, new Rectangle(), iconRect,
        checkBox.getText() == null ? 0 : checkBox.getIconTextGap());
      indent = iconRect.x;
    }
    return indent + checkBox.getIconTextGap();
  }
}