// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author oleg
 * This component is used to configure list of folders with add/remove buttons.
 */
public class PathsChooserComponent implements ComponentWithEmptyText {
  private final JPanel myContentPane;
  private final JBList myList;
  private final DefaultListModel myListModel;

  private List<@NlsSafe String> myWorkingCollection;
  private final List<@NlsSafe String> myInitialCollection;
  @Nullable private final Project myProject;

  public PathsChooserComponent(@NotNull final List<String> collection, @NotNull final PathProcessor processor) {
    this(collection, processor, null);
  }

  public PathsChooserComponent(@NotNull final List<String> collection,
                               @NotNull final PathProcessor processor,
                               @Nullable final Project project) {
    myList = new JBList();
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myInitialCollection = collection;
    myProject = project;
    myWorkingCollection = new ArrayList<>(myInitialCollection);
    myListModel = new DefaultListModel();
    myList.setModel(myListModel);

    myContentPane = ToolbarDecorator.createDecorator(myList).disableUpDownActions().setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        final FileChooserDescriptor dirChooser = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        dirChooser.setShowFileSystemRoots(true);
        dirChooser.setHideIgnored(true);
        dirChooser.setTitle(UIBundle.message("file.chooser.default.title"));
        FileChooser.chooseFiles(dirChooser, myProject, null, files -> {
          for (VirtualFile file : files) {
            // adding to the end
            final String path = file.getPresentableUrl();
            if (processor.addPath(myWorkingCollection, path)) {
              myListModel.addElement(path);
            }
          }
        });
      }
    }).setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        int selected = myList.getSelectedIndex();
        if (selected != -1) {
          // removing index
          final String path = (String) myListModel.get(selected);
          if (processor.removePath(myWorkingCollection, path)){
            myListModel.remove(selected);
          }
        }
      }
    }).createPanel();

    // fill list
    reset();
  }

  @NotNull
  @Override
  public StatusText getEmptyText() {
    return myList.getEmptyText();
  }

  public JPanel getContentPane() {
    return myContentPane;
  }

  public List<String> getValues(){
    return myWorkingCollection;
  }

  public void reset() {
    myListModel.clear();
    myWorkingCollection = new ArrayList<>(myInitialCollection);
    for (@NlsSafe String path : myWorkingCollection) {
      myListModel.addElement(path);
    }
  }

  public boolean isModified() {
    return !myWorkingCollection.equals(myInitialCollection);
  }

  public void apply() {
    myInitialCollection.clear();
    myInitialCollection.addAll(myWorkingCollection);
  }

  public interface PathProcessor {
    boolean addPath(List<String> paths, String path);
    boolean removePath(List<String> paths, String path);
  }
}
