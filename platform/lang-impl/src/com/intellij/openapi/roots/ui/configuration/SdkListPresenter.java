// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.SdkAppearanceService;
import com.intellij.openapi.roots.ui.configuration.SdkListItem.GroupItem;
import com.intellij.openapi.roots.ui.configuration.SdkListItem.SdkItem;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.Producer;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.Objects;

import static com.intellij.openapi.roots.ui.configuration.SdkListItem.*;

public final class SdkListPresenter extends ColoredListCellRenderer<SdkListItem> {
  private static final Icon EMPTY_ICON = EmptyIcon.create(1, 16);
  @NotNull private final Producer<SdkListModel> myGetModel;

  public SdkListPresenter(@NotNull Producer<SdkListModel> getSdkListModel) {
    myGetModel = getSdkListModel;
  }

  @NotNull
  public <T> ListCellRenderer<T> forType(@NotNull Function<? super T, ? extends SdkListItem> unwrap) {
    return new ListCellRenderer<T>() {
      @NotNull
      @Override
      public Component getListCellRendererComponent(JList<? extends T> list,
                                                    @Nullable T value,
                                                    int index,
                                                    boolean isSelected,
                                                    boolean cellHasFocus) {
        SdkListItem item = value == null ? null : unwrap.fun(value);
        //noinspection unchecked,rawtypes
        return SdkListPresenter.this.getListCellRendererComponent((JList)list, item, index, isSelected, cellHasFocus);
      }
    };
  }

  @Override
  public Component getListCellRendererComponent(@NotNull JList<? extends SdkListItem> list,
                                                @Nullable SdkListItem value,
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

    SdkListModel model = myGetModel.produce();
    //handle the selected item to show in the ComboBox, not in the popup
    if (index == -1) {
      component.setOpaque(false);
      panel.setOpaque(false);
      if (model.isSearching()) {
        JBLabel progressIcon = new JBLabel(AnimatedIcon.Default.INSTANCE);
        panel.add(progressIcon, BorderLayout.EAST);
      }
      return panel;
    }

    component.setOpaque(true);
    panel.setOpaque(true);
    panel.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
    if (value instanceof GroupItem) {
      JBLabel toggle = new JBLabel(AllIcons.Icons.Ide.NextStep);
      toggle.setOpaque(false);
      panel.add(toggle, BorderLayout.EAST);
    }

    String separatorTextAbove = value != null ? model.getSeparatorTextAbove(value) : null;
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
  protected void customizeCellRenderer(@NotNull JList<? extends SdkListItem> list,
                                       @Nullable SdkListItem value,
                                       int index,
                                       boolean selected,
                                       boolean hasFocus) {

    setIcon(EMPTY_ICON);    // to fix vertical size
    if (value instanceof InvalidSdkItem) {
      InvalidSdkItem item = (InvalidSdkItem)value;
      final String str = ProjectBundle.message("jdk.combo.box.invalid.item", item.getSdkName());
      append(str, SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
    else if (value instanceof ProjectSdkItem) {
      final Sdk sdk = myGetModel.produce().resolveProjectSdk();
      if (sdk != null) {
        setIcon(((SdkType)sdk.getSdkType()).getIcon());
        append(ProjectBundle.message("project.roots.project.jdk.inherited"), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        append(" " + sdk.getName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      else {
        String str = ProjectBundle.message("jdk.combo.box.project.item");
        append(str, SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }
    else if (value instanceof SuggestedItem) {
      SuggestedItem item = (SuggestedItem)value;
      SdkType type = item.getSdkType();
      String home = item.getHomePath();
      String version = item.getVersion();

      Icon icon1 = type.getIconForAddAction();
      if (Objects.equals(icon1, IconUtil.getAddIcon())) icon1 = type.getIcon();
      if (icon1 == null) icon1 = IconUtil.getAddIcon();
      Icon icon = icon1;
      setIcon(icon);
      append(presentDetectedSdkPath(home));
      append(" " + version, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
    else if (value instanceof ActionItem) {
      ActionItem item = (ActionItem)value;
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
    else if (value instanceof GroupItem) {
      GroupItem item = (GroupItem)value;
      setIcon(item.myIcon);
      append(item.myCaption);
    }
    else if (value instanceof SdkItem) {
      Sdk sdk = ((SdkItem)value).getSdk();
      SdkAppearanceService.getInstance()
        .forSdk(sdk, false, selected, false)
        .customize(this);

      String version = sdk.getVersionString();
      if (version == null) version = ((SdkType)sdk.getSdkType()).getPresentableName();
      append(" " + version, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
    else if (value instanceof NoneSdkItem) {
      SdkAppearanceService.getInstance()
        .forSdk(null, false, selected, false)
        .customize(this);
    }
    else {
      customizeCellRenderer(list, new NoneSdkItem(), index, selected, hasFocus);
    }
  }

  @NotNull
  public static String presentDetectedSdkPath(@NotNull String home) {
    //for macOS, let's try removing Bundle internals
    home = StringUtil.trimEnd(home, "/Contents/Home");
    home = StringUtil.trimEnd(home, "/Contents/MacOS");
    home = StringUtil.shortenTextWithEllipsis(home, 50, 30);
    return home;
  }
}
