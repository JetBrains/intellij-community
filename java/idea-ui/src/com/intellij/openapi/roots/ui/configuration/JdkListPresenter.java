// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.SdkAppearanceService;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox.ActionGroupJdkItem;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox.ActionJdkItem;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox.ActualJdkComboBoxItem;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox.JdkComboBoxItem;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.Objects;

import static com.intellij.openapi.roots.ui.configuration.JdkComboBox.*;

public abstract class JdkListPresenter extends ColoredListCellRenderer<JdkComboBoxItem> {
  private static final Icon EMPTY_ICON = EmptyIcon.create(1, 16);

  @NotNull
  private final ProjectSdksModel mySdkModel;

  protected JdkListPresenter(@NotNull ProjectSdksModel sdkModel) {
    mySdkModel = sdkModel;
  }

  @NotNull
  protected abstract JdkListModel getModel();

  protected abstract boolean showProgressIcon();

  @Override
  public Component getListCellRendererComponent(JList<? extends JdkComboBoxItem> list,
                                                JdkComboBoxItem value,
                                                int index,
                                                boolean selected,
                                                boolean hasFocus) {

    SimpleColoredComponent component = (SimpleColoredComponent)super.getListCellRendererComponent(list, value, index, selected, hasFocus);
    JPanel panel = new JPanel(new BorderLayout()) {
      @Override
      public void setBorder(Border border) {
        // we do not want to outer UI to add a border to that JPanel
        // see com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI.CustomComboPopup#customizeListRendererComponent
        component.setBorder(border);
      }
    };
    panel.add(component, BorderLayout.CENTER);

    JdkListModel model = getModel();
    //handle the selected item to show in the ComboBox, not in the popup
    if (index == -1) {
      component.setOpaque(false);
      panel.setOpaque(false);
      if (model.isSearching() && showProgressIcon()) {
        JBLabel progressIcon = new JBLabel(AnimatedIcon.Default.INSTANCE);
        panel.add(progressIcon, BorderLayout.EAST);
      }
      return panel;
    }

    component.setOpaque(true);
    panel.setOpaque(true);
    panel.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
    if (value instanceof ActionGroupJdkItem) {
      JBLabel toggle = new JBLabel(AllIcons.Icons.Ide.NextStep);
      toggle.setOpaque(false);
      panel.add(toggle, BorderLayout.EAST);
    }

    String separatorTextAbove = model.getSeparatorTextAbove(value);
    if (separatorTextAbove != null) {
      SeparatorWithText separator = new SeparatorWithText();
      if (!separatorTextAbove.isEmpty()) {
        separator.setCaption(separatorTextAbove);
      }
      separator.setOpaque(false);
      separator.setBackground(list.getBackground());

      JPanel wrapper = new JPanel(new BorderLayout());
      wrapper.add(separator, BorderLayout.CENTER);
      wrapper.setBackground(list.getBackground());
      wrapper.setOpaque(true);

      panel.add(wrapper, BorderLayout.NORTH);
    }
    return panel;
  }

  @Override
  protected void customizeCellRenderer(@NotNull JList<? extends JdkComboBoxItem> list,
                                       JdkComboBoxItem value,
                                       int index,
                                       boolean selected,
                                       boolean hasFocus) {

    setIcon(EMPTY_ICON);    // to fix vertical size
    if (value instanceof InvalidJdkComboBoxItem) {
      final String str = ProjectBundle.message("jdk.combo.box.invalid.item", value.getSdkName());
      append(str, SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
    else if (value instanceof ProjectJdkComboBoxItem) {
      final Sdk jdk = mySdkModel.getProjectSdk();
      if (jdk != null) {
        setIcon(((SdkType)jdk.getSdkType()).getIcon());
        append(ProjectBundle.message("project.roots.project.jdk.inherited"), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        append(" " + jdk.getName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      else {
        String str = ProjectBundle.message("jdk.combo.box.project.item");
        append(str, SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }
    else if (value instanceof SuggestedJdkItem) {
      SuggestedJdkItem item = (SuggestedJdkItem)value;
      SdkType type = item.getSdkType();
      String home = item.getPath();
      String version = item.getVersion();

      Icon icon1 = type.getIconForAddAction();
      if (Objects.equals(icon1, IconUtil.getAddIcon())) icon1 = type.getIcon();
      if (icon1 == null) icon1 = IconUtil.getAddIcon();
      Icon icon = icon1;
      setIcon(icon);
      //for macOS, let's try removing Bundle internals
      home = StringUtil.trimEnd(home, "/Contents/Home");
      home = StringUtil.trimEnd(home, "/Contents/MacOS");
      home = StringUtil.shortenTextWithEllipsis(home, 50, 30);
      append(home);
      if (version == null) version = type.getPresentableName();
      append(" " + version, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
    else if (value instanceof ActionJdkItem) {
      ActionJdkItem item = (ActionJdkItem)value;
      Presentation template = item.myAction.getTemplatePresentation();
      //this is a sub-menu item
      SdkType sdkType = item.myAction.getSdkType();
      if (item.myGroup != null) {
        switch (item.myRole) {
          case ADD:
            //we already have the (+) in the parent node, thus showing original icon
            Icon icon = sdkType.getIcon();
            if (icon == null) icon = AllIcons.General.Add;
            setIcon(icon);
            append(sdkType.getPresentableName() + "...");
            break;
          case DOWNLOAD:
            setIcon(template.getIcon());
            append("Download " + sdkType.getPresentableName() + "...");
            break;
        }
      }
      else {
        switch (item.myRole) {
          case ADD:
            setIcon(template.getIcon());
            append("Add " + sdkType.getPresentableName() + "...");
            break;
          case DOWNLOAD:
            setIcon(template.getIcon());
            append("Download " + sdkType.getPresentableName() + "...");
            break;
        }
      }
    }
    else if (value instanceof ActionGroupJdkItem) {
      ActionGroupJdkItem item = (ActionGroupJdkItem)value;
      setIcon(item.myIcon);
      append(item.myCaption);
    }
    else if (value instanceof ActualJdkComboBoxItem || value instanceof NoneJdkComboBoxItem) {
      Sdk sdk = value.getJdk();
      SdkAppearanceService.getInstance()
        .forSdk(sdk, false, selected, false)
        .customize(this);

      if (sdk != null) {
        String version = sdk.getVersionString();
        if (version == null) version = ((SdkType)sdk.getSdkType()).getPresentableName();
        append(" " + version, SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }
    else {
      customizeCellRenderer(list, new NoneJdkComboBoxItem(), index, selected, hasFocus);
    }
  }
}
