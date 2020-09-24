// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl.associate.ui;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FileTypeAssociationForm {

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
    myScrollPane.setBorder(JBUI.Borders.empty());
    myDescLabel.setText(
      FileTypesBundle.message("filetype.associate.dialog.label", ApplicationInfo.getInstance().getFullApplicationName()));
    myBottomInfoLabel.setText(FileTypesBundle.message("filetype.associate.info.label"));
    myBottomInfoLabel.setFont(UIUtil.getFont(UIUtil.FontSize.SMALL, myBottomInfoLabel.getFont()));
    myBottomInfoLabel.setForeground(JBColor.GRAY);
  }

  private void createUIComponents() {
    myItems = createItems();
    DefaultListModel<JCheckBox> model = new DefaultListModel<>();
    myFileTypesList = new CheckBoxList<>(model) {
      @Override
      protected JComponent adjustRendering(JComponent rootComponent,
                                           JCheckBox checkBox,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        Color textColor = getForeground(selected);
        Color backgroundColor = getBackground(selected);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(checkBox, BorderLayout.LINE_START);
        JLabel infoLabel = new JLabel(myItems.get(index).getExtensionList(), SwingConstants.LEFT);
        infoLabel.setBorder(JBUI.Borders.emptyLeft(JBUIScale.scale(8)));
        panel.add(infoLabel, BorderLayout.CENTER);
        panel.setBackground(backgroundColor);
        infoLabel.setForeground(selected ? textColor : JBColor.GRAY);
        infoLabel.setBackground(backgroundColor);
        return panel;
      }
    };
    model.addAll(myItems);
    // noinspection rawtypes,unchecked,unchecked
    new ListSpeedSearch<>(myFileTypesList, (Function<Object, String>)o -> ((MyFileTypeItem)o).getText());
  }

  public JPanel getTopPanel() {
    return myTopPanel;
  }

  public List<MyFileTypeItem> createItems() {
    List<MyFileTypeItem> items = new ArrayList<>();
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    for (FileType fileType : fileTypeManager.getRegisteredFileTypes()) {
      if (fileType instanceof LanguageFileType &&
          !(fileType instanceof PlainTextFileType) &&
          !fileTypeManager.getAssociations(fileType).isEmpty()) {
        items.add(new MyFileTypeItem(fileType));
      }
    }
    Collections.sort(items);
    return new ArrayList<>(items);
  }

  private static final class MyFileTypeItem extends JCheckBox implements Comparable<MyFileTypeItem> {
    private final FileType myFileType;

    private MyFileTypeItem(FileType fileType) {
      myFileType = fileType;
      setText(myFileType.getDisplayName());
    }

    private @NlsSafe String getExtensionList() {
      List<FileNameMatcher> matchers = FileTypeManager.getInstance().getAssociations(myFileType);
      return String.join(", ", ContainerUtil.map(matchers, FileNameMatcher::getPresentableString));
    }

    @Override
    public int compareTo(@NotNull FileTypeAssociationForm.MyFileTypeItem item) {
      return myFileType.getDisplayName().compareTo(item.myFileType.getDisplayName());
    }
  }

  List<FileType> getSelectedFileTypes() {
    return myItems.stream()
      .filter(item -> item.isSelected())
      .map(item -> item.myFileType).collect(Collectors.toList());
  }
}
