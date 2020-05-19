// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.actions.associate.ui;

import com.intellij.execution.Platform;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FileTypeAssociationForm {

  private JPanel myFileTypesPanel;
  private JPanel myTopPanel;
  private JBScrollPane myScrollPane;
  private JBLabel myDescLabel;

  private List<MyFileTypeItem> myItems;

  public FileTypeAssociationForm() {
    myTopPanel.setPreferredSize(JBDimension.create(new Dimension(800, 600)));
    myTopPanel.setBorder(JBUI.Borders.empty());
    myScrollPane.setBorder(JBUI.Borders.empty());
    myDescLabel.setText(ApplicationBundle.message("light.edit.file.types.open.with.label", ApplicationInfo.getInstance().getFullApplicationName()));
  }

  private void createUIComponents() {
    myFileTypesPanel = new JPanel();
    myFileTypesPanel.setBorder(JBUI.Borders.empty());
    myFileTypesPanel.setLayout(new BoxLayout(myFileTypesPanel, BoxLayout.Y_AXIS));
    myItems = createItems();
    myItems.forEach(item -> myFileTypesPanel.add(item.myCheckBox));
  }

  public JPanel getTopPanel() {
    return myTopPanel;
  }

  public List<MyFileTypeItem> createItems() {
    List<MyFileTypeItem> items = new ArrayList<>();
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    for (FileType fileType : fileTypeManager.getRegisteredFileTypes()) {
      if (fileType instanceof LanguageFileType && !fileTypeManager.getAssociations(fileType).isEmpty()) {
        items.add(new MyFileTypeItem(fileType));
      }
    }
    Collections.sort(items);
    return items;
  }

  private static class MyFileTypeItem implements Comparable<MyFileTypeItem> {
    private final FileType myFileType;
    private final JBCheckBox myCheckBox;

    private MyFileTypeItem(FileType fileType) {
      myFileType = fileType;
      myCheckBox = new JBCheckBox(getName());
    }

    private String getName() {
      StringBuilder nameBuilder = new StringBuilder();
      nameBuilder.append(myFileType.getDisplayName()).append(" (");
      List<FileNameMatcher> matchers = FileTypeManager.getInstance().getAssociations(myFileType);
      Supplier<String> commaSupplier = new Supplier<String>() {
        private boolean isFirst = true;
        @Override
        public String get() {
          if (isFirst) {
            isFirst = false;
            return "";
          }
          return ", ";
        }
      };
      matchers.stream().map(FileNameMatcher::getPresentableString).forEach(matcherStr->{
        nameBuilder.append(commaSupplier.get()).append(matcherStr);
      });
      nameBuilder.append(")");
      return nameBuilder.toString();
    }

    @Override
    public int compareTo(@NotNull FileTypeAssociationForm.MyFileTypeItem item) {
      return myFileType.getDisplayName().compareTo(item.myFileType.getDisplayName());
    }
  }

  List<FileType> getSelectedFileTypes() {
    return myItems.stream()
      .filter(item -> item.myCheckBox.isSelected())
      .map(item -> item.myFileType).collect(Collectors.toList());
  }
}
