/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.util.Consumer;
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
  private JPanel myContentPane;
  private JBList myList;
  private final DefaultListModel myListModel;

  private List<String> myWorkingCollection;
  private final List<String> myInitialCollection;
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
            final String path = file.getPath();
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
    for (String path : myWorkingCollection) {
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
