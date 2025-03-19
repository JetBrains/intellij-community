// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl.associate.ui;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.fileTypes.impl.associate.OSAssociateFileTypesUtil;
import com.intellij.openapi.fileTypes.impl.associate.OSFileAssociationPreferences;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.CheckBoxListListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ThreeStateCheckBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class FileTypeAssociationForm {

  private static final int DEFAULT_EXTENSION_SPLIT_THRESHOLD = 5;

  private JPanel               myTopPanel;
  private JBScrollPane         myScrollPane;
  private JBLabel              myDescLabel;
  @SuppressWarnings({"unused", "rawtypes"}) // UI form
  private JBList               myFileTypesList;
  private JBLabel              myBottomInfoLabel;
  private List<MyFileTypeItem> myItems;

  public FileTypeAssociationForm() {
    myTopPanel.setPreferredSize(JBDimension.create(new Dimension(800, 600)));
    myTopPanel.setBorder(JBUI.Borders.empty());
    myScrollPane.setBorder(JBUI.Borders.customLine(JBColor.border()));
    myDescLabel.setText(
      FileTypesBundle.message("filetype.associate.dialog.label", ApplicationInfo.getInstance().getFullApplicationName()));
    myBottomInfoLabel.setText(FileTypesBundle.message("filetype.associate.info.label"));
    myBottomInfoLabel.setFont(UIUtil.getFont(UIUtil.FontSize.SMALL, myBottomInfoLabel.getFont()));
    myBottomInfoLabel.setForeground(JBColor.GRAY);
  }

  private void createUIComponents() {
    myItems = createItems();
    presetItems();
    DefaultListModel<JCheckBox> model = new DefaultListModel<>();
    CheckBoxList<MyFileTypeItem> checkBoxList = new CheckBoxList<>(model) {
      @Override
      protected JComponent adjustRendering(JComponent rootComponent,
                                           JCheckBox checkBox,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        MyFileTypeItem item = myItems.get(index);
        Color textColor = getForeground(selected);
        Color backgroundColor = getBackground(selected);
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 2)) {
          private final AccessibleContext myContext = checkBox.getAccessibleContext();

          @Override
          public AccessibleContext getAccessibleContext() {
            return myContext;
          }
        };
        if (item.isSubType()) {
          panel.add(Box.createRigidArea(new Dimension(JBUIScale.scale(20),1)));
        }
        panel.add(checkBox);
        if (!item.isSubType() && !item.isGroupItem()) {
          JLabel infoLabel = new JLabel(item.getExtensionList(), SwingConstants.LEFT);
          infoLabel.setBorder(JBUI.Borders.emptyLeft(JBUIScale.scale(8)));
          panel.add(infoLabel);
          panel.setBackground(backgroundColor);
          infoLabel.setForeground(selected ? textColor : JBColor.GRAY);
          infoLabel.setBackground(backgroundColor);
          panel.getAccessibleContext().setAccessibleDescription(infoLabel.getText());
        }
        panel.setBackground(backgroundColor);
        return panel;
      }

      @Override
      protected @Nullable Point findPointRelativeToCheckBox(int x, int y, @NotNull JCheckBox checkBox, int index) {
        return super.findPointRelativeToCheckBoxWithAdjustedRendering(x, y, checkBox, index);
      }
    };
    model.addAll(myItems);
    checkBoxList.setCheckBoxListListener(new CheckBoxListListener() {
      @Override
      public void checkBoxSelectionChanged(int index, boolean value) {
        onItemStateChange(myItems.get(index), value);
      }
    });
    myFileTypesList = checkBoxList;
    if (!myFileTypesList.isEmpty()) myFileTypesList.setSelectedIndex(0);
    // noinspection rawtypes,unchecked
    ListSpeedSearch.installOn(myFileTypesList, o -> ((MyFileTypeItem)o).getText());
  }

  public JPanel getTopPanel() {
    return myTopPanel;
  }

  @Nullable
  JComponent getPreferredFocusedComponent() {
    return myFileTypesList;
  }

  public List<MyFileTypeItem> createItems() {
    List<MyFileTypeItem> items = new ArrayList<>();
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    for (FileType fileType : fileTypeManager.getRegisteredFileTypes()) {
      if (isSupported(fileType)) {
        int extCount = OSAssociateFileTypesUtil.getExtensionMatchers(fileType).size();
        if (extCount > 0) {
          if (splitExtensions(fileType, extCount)) {
            items.add(new MyFileTypeItem(fileType, null, true));
            OSAssociateFileTypesUtil.createSubtypes(fileType).forEach(
              subtype -> items.add(new MyFileTypeItem(subtype, fileType, false))
            );
          }
          else {
            items.add(new MyFileTypeItem(fileType, null, false));
          }
        }
      }
    }
    Collections.sort(items);
    return items;
  }

  private void presetItems() {
    OSFileAssociationPreferences preferences = OSFileAssociationPreferences.getInstance();
    myItems.forEach(
      item -> {
        boolean selected = preferences.contains(item.myFileType);
        item.setSelected(selected);
        onItemStateChange(item, selected);
      }
    );
  }

  private static boolean splitExtensions(@NotNull FileType fileType, int extCount) {
    var extensionMode = fileType instanceof OSFileIdeAssociation type ?
                        type.getExtensionMode() :
                        OSFileIdeAssociation.ExtensionMode.Default;
    return switch (extensionMode) {
      case Selected -> true;
      case All -> false;
      default -> extCount > DEFAULT_EXTENSION_SPLIT_THRESHOLD;
    };
  }

  private static boolean isSupported(@NotNull FileType fileType) {
    return !(fileType instanceof NativeFileType) &&
           !(fileType instanceof UserBinaryFileType) &&
           !(fileType instanceof ArchiveFileType) &&
           !(fileType instanceof FakeFileType) &&
           (!(fileType instanceof OSFileIdeAssociation) ||
            ((OSFileIdeAssociation)fileType).isFileAssociationAllowed());
  }

  private void onItemStateChange(@NotNull MyFileTypeItem currItem, boolean isSelected) {
    if (currItem.isGroupItem()) {
      myItems.stream()
             .filter(subItem -> subItem.mySuperType == currItem.myFileType)
             .forEach(subItem -> subItem.setSelected(isSelected));
    }
    else if (currItem.mySuperType != null) {
      for (MyFileTypeItem item : myItems) {
        if (item.myFileType == currItem.mySuperType) {
          item.setState(getSuperItemState(currItem));
          break;
        }
      }
    }
  }

  private ThreeStateCheckBox.State getSuperItemState(@NotNull MyFileTypeItem subItem) {
    List<MyFileTypeItem> withSameSupertype = ContainerUtil.filter(myItems, item -> item.mySuperType == subItem.mySuperType);
    long selected = withSameSupertype.stream().filter(item -> item.isSelected()).count();
    if (selected == 0) return ThreeStateCheckBox.State.NOT_SELECTED;
    if (selected == withSameSupertype.size()) return ThreeStateCheckBox.State.SELECTED;
    return ThreeStateCheckBox.State.DONT_CARE;
  }


  private static final class MyFileTypeItem extends ThreeStateCheckBox implements Comparable<MyFileTypeItem> {
    private final FileType myFileType;
    private final @Nullable FileType mySuperType;
    private final boolean myIsGroupItem;

    private MyFileTypeItem(@NotNull FileType fileType, @Nullable FileType superType, boolean isGroupItem) {
      myFileType = fileType;
      mySuperType = superType;
      myIsGroupItem = isGroupItem;
      setSelected(false);
      setText(myFileType.getDescription());
    }

    private @NlsSafe String getExtensionList() {
      List<ExtensionFileNameMatcher> matchers = OSAssociateFileTypesUtil.getExtensionMatchers(myFileType);
      return String.join(", ", ContainerUtil.map(matchers, FileNameMatcher::getPresentableString));
    }

    @Override
    public int compareTo(@NotNull FileTypeAssociationForm.MyFileTypeItem item) {
      if (mySuperType != item.mySuperType) {
        String d1 = mySuperType != null ? mySuperType.getDescription() : myFileType.getDescription();
        String d2 = item.mySuperType != null ? item.mySuperType.getDescription() : item.myFileType.getDescription();
        return d1.compareTo(d2);
      }
      int subtypePriority = Boolean.compare(isMainSubtype(), item.isMainSubtype());
      if (subtypePriority != 0) return -subtypePriority;
      return myFileType.getDescription().compareTo(item.myFileType.getDescription());
    }

    private boolean isSubType() {
      return mySuperType != null;
    }

    private boolean isMainSubtype() {
      FileNameMatcher subtypeMatcher = OSAssociateFileTypesUtil.getSubtypeMatcher(myFileType);
      return mySuperType != null &&
             subtypeMatcher instanceof ExtensionFileNameMatcher &&
             mySuperType.getDefaultExtension().equals(((ExtensionFileNameMatcher)subtypeMatcher).getExtension());
    }

    private boolean isGroupItem() {
      return myIsGroupItem;
    }
  }

  List<FileType> getSelectedFileTypes() {
    return myItems.stream()
      .filter(item -> item.isSelected() && !item.isGroupItem())
      .map(item -> item.myFileType).collect(Collectors.toList());
  }
}
