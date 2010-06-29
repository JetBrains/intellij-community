/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexey Kudravtsev
 */
abstract class FileTemplateTabAsList extends FileTemplateTab {
  private final JList myList = new JBList();
  private MyListModel myModel;

  FileTemplateTabAsList(String title) {
    super(title);
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(new MyListCellRenderer());
    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        onTemplateSelected();
      }
    });
  }

  private class MyListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      Icon icon = null;
      if (value instanceof FileTemplate) {
        FileTemplate template = (FileTemplate) value;
        icon = FileTypeManager.getInstance().getFileTypeByExtension(template.getExtension()).getIcon();
        final boolean internalTemplate = AllFileTemplatesConfigurable.isInternalTemplate(template.getName(), getTitle());
        if (internalTemplate) {
          setFont(getFont().deriveFont(Font.BOLD));
          setText(FileTemplateManagerImpl.getInstanceImpl().localizeInternalTemplateName(template));
        }
        else {
          setFont(getFont().deriveFont(Font.PLAIN));
          setText(template.getName());
        }

        if (!template.isDefault()) {
          if (!isSelected) {
            setForeground(MODIFIED_FOREGROUND);
          }
        }
      }
      setIcon(icon);
      return this;
    }
  }

  public void removeSelected() {
    final FileTemplate selectedTemplate = getSelectedTemplate();
    if (selectedTemplate == null) return;
    DefaultListModel model = (DefaultListModel) myList.getModel();
    int selectedIndex = myList.getSelectedIndex();
    model.remove(selectedIndex);
    if (!model.isEmpty()) {
      myList.setSelectedIndex(Math.min(selectedIndex, model.size() - 1));
    }
    onTemplateSelected();
//      myModified = true;
//      fireListChanged();
//      onListSelectionChanged();
  }

  private static class MyListModel extends DefaultListModel {
    public void fireListDataChanged() {
      int size = getSize();
      if (size > 0) {
        fireContentsChanged(this, 0, size - 1);
      }
    }
  }

  protected void initSelection(FileTemplate selection) {
    myModel = new MyListModel();
    myList.setModel(myModel);
    final FileTemplate[] templates = savedTemplates.values().toArray(new FileTemplate[savedTemplates.values().size()]);
    for (FileTemplate template : templates) {
      myModel.addElement(template);
    }
    if (selection != null) {
      selectTemplate(selection);
    }
    else if (myList.getModel().getSize() > 0) myList.setSelectedIndex(0);
  }

  public void fireDataChanged() {
    myModel.fireListDataChanged();
  }

  @NotNull
  public FileTemplate[] getTemplates() {
    final int size = myModel.getSize();
    List<FileTemplate> templates = new ArrayList<FileTemplate>(size);
    for (int i =0; i<size; i++) {
      templates.add((FileTemplate) myModel.getElementAt(i));
    }
    return templates.toArray(new FileTemplate[templates.size()]);
  }

  public void addTemplate(FileTemplate newTemplate) {
    myModel.addElement(newTemplate);
  }

  public void selectTemplate(FileTemplate template) {
    myList.setSelectedValue(template, true);
  }

  public FileTemplate getSelectedTemplate() {
    final Object value = myList.getSelectedValue();
    return value instanceof FileTemplate ? (FileTemplate) value : null;
  }

  public JComponent getComponent() {
    return myList;
  }
}
