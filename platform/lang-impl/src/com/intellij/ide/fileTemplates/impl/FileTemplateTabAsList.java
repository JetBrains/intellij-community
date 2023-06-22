// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

abstract class FileTemplateTabAsList extends FileTemplateTab {
  private final JList<FileTemplate> myList = new JBList<>();
  private MyListModel myModel;

  FileTemplateTabAsList(@NlsContexts.TabTitle String title) {
    super(title);
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      label.setIcon(FileTemplateUtil.getIcon(value));
      label.setText(value.getName());
      if (!value.isDefault() && myList.getSelectedIndex() != index) {
        label.setForeground(MODIFIED_FOREGROUND);
      }
      if (FileTemplateBase.isChild(value)) {
        label.setBorder(JBUI.Borders.emptyLeft(JBUI.scale(20)));
        label.setText(value.getFileName().isEmpty() ? IdeBundle.message("label.empty.file.name") : value.getFileName());  //NON-NLS
      }
    }));
    myList.addListSelectionListener(__ -> onTemplateSelected());
    ListSpeedSearch.installOn(myList, FileTemplate::getName);
  }

  @Override
  public void removeSelected() {
    final FileTemplate selectedTemplate = getSelectedTemplate();
    if (selectedTemplate == null) {
      return;
    }
    DefaultListModel<?> model = (DefaultListModel<?>) myList.getModel();
    for (FileTemplate child : selectedTemplate.getChildren()) {
      model.removeElement(child);
    }
    final int selectedIndex = myList.getSelectedIndex();
    model.remove(selectedIndex);
    if (!model.isEmpty()) {
      myList.setSelectedIndex(Math.min(selectedIndex, model.size() - 1));
    }
    onTemplateSelected();
  }

  private static final class MyListModel extends DefaultListModel<FileTemplate> {
    private void fireListDataChanged() {
      int size = getSize();
      if (size > 0) {
        fireContentsChanged(this, 0, size - 1);
      }
    }
  }

  @Override
  protected void initSelection(FileTemplate selection) {
    myModel = new MyListModel();
    myList.setModel(myModel);
    for (FileTemplate template : templates) {
      myModel.addElement(template);
    }
    if (selection != null) {
      selectTemplate(selection);
    }
    else if (myList.getModel().getSize() > 0) {
      myList.setSelectedIndex(0);
    }
  }

  @Override
  public void fireDataChanged() {
    myModel.fireListDataChanged();
  }

  @Override
  public @NotNull List<FileTemplate> getTemplates() {
    int size = myModel.getSize();
    List<FileTemplate> templates = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      templates.add(myModel.getElementAt(i));
    }
    return templates;
  }

  @Override
  public void addTemplate(FileTemplate newTemplate) {
    myModel.addElement(newTemplate);
  }

  @Override
  public void insertTemplate(FileTemplate newTemplate, int index) {
    myModel.insertElementAt(newTemplate, index);
  }

  @Override
  public void selectTemplate(FileTemplate template) {
    myList.setSelectedValue(template, true);
  }

  @Override
  public FileTemplate getSelectedTemplate() {
    return myList.getSelectedValue();
  }

  @Override
  public JComponent getComponent() {
    return myList;
  }
}
