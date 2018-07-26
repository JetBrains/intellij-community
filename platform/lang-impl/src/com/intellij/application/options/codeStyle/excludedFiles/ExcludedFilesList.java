// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.excludedFiles;

import com.intellij.formatting.fileSet.FileSetDescriptor;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class ExcludedFilesList extends JBList<String> {

  private final ToolbarDecorator myFileListDecorator;
  private DefaultListModel<String> myModel;

  public ExcludedFilesList() {
    super();
    myFileListDecorator = ToolbarDecorator.createDecorator(this)
      .setAddAction(
        new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            addFileSpec();
          }
        })
      .setRemoveAction(
        new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            removeFileSpec();
          }
        })
      .setEditAction(
        new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            editFileSpec();
          }
        }
      ).disableUpDownActions();
    addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        onSelectionChange();
      }
    });
  }

  public void initModel() {
    myModel = createDefaultListModel(ArrayUtil.EMPTY_STRING_ARRAY);
    setModel(myModel);
  }

  private void onSelectionChange() {
    int i = getSelectedIndex();
    AnActionButton removeButton = ToolbarDecorator.findRemoveButton(myFileListDecorator.getActionsPanel());
    AnActionButton editButton = ToolbarDecorator.findEditButton(myFileListDecorator.getActionsPanel());
    removeButton.setEnabled(i >= 0);
    editButton.setEnabled(i >= 0);
  }

  public void reset(@NotNull CodeStyleSettings settings) {
    myModel.clear();
    for (FileSetDescriptor descriptor : settings.getExcludedFiles().getDescriptors()) {
      myModel.addElement(descriptor.getPattern());
    }
  }

  public void apply(@NotNull CodeStyleSettings settings) {
    settings.getExcludedFiles().clear();
    for (int i = 0; i < myModel.getSize(); i ++) {
      settings.getExcludedFiles().addDescriptor(myModel.get(i));
    }
  }

  public boolean isModified(@NotNull CodeStyleSettings settings) {
    if (myModel.size() != settings.getExcludedFiles().getDescriptors().size()) return true;
    for (int i = 0; i < myModel.getSize(); i ++) {
      if (!myModel.get(i).equals(settings.getExcludedFiles().getDescriptors().get(i).getPattern())) {
        return true;
      }
    }
    return false;
  }

  public ToolbarDecorator getDecorator() {
    return myFileListDecorator;
  }

  private void addFileSpec() {
    ExcludedFilesDialog dialog = new ExcludedFilesDialog();
    dialog.show();
    if (dialog.isOK() && dialog.getFileSpec() != null) {
      int insertAt = getSelectedIndex() < 0 ? getItemsCount() : getSelectedIndex() + 1;
      String fileSpec = dialog.getFileSpec();
      int exiting = myModel.indexOf(fileSpec);
      if (exiting < 0) {
        myModel.add(insertAt, fileSpec);
        setSelectedValue(fileSpec, true);
      }
      else {
        setSelectedValue(myModel.get(exiting), true);
      }
    }
  }

  private void removeFileSpec() {
    int i = getSelectedIndex();
    if (i >= 0) {
      myModel.remove(i);
    }
  }

  private void editFileSpec() {
    int i = getSelectedIndex();
    if (i >= 0) {
      ExcludedFilesDialog dialog = new ExcludedFilesDialog(myModel.get(i));
      dialog.show();
      if (dialog.isOK()) {
        String fileSpec = dialog.getFileSpec();
        if (fileSpec != null) {
          int existing = myModel.indexOf(fileSpec);
          if (existing < 0) {
            myModel.set(i, fileSpec);
          }
          else {
            if (existing != i) {
              setSelectedValue(myModel.get(existing), true);
              myModel.remove(i);
            }
          }
        }
        else {
          myModel.remove(i);
        }
      }
    }
  }

}
