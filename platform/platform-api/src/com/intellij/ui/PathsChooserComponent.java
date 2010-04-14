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
package com.intellij.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author oleg
 * This component is used to configure list of folders with add/remove buttons.
 */
public class PathsChooserComponent {
  private JPanel myContentPane;
  private JList myList;
  private JButton myAddButton;
  private JButton myRemoveButton;
  private final DefaultListModel myListModel;

  private List<String> myWorkingCollection;
  private final List<String> myInitialCollection;

  public PathsChooserComponent(@NotNull final List<String> collection, @NotNull final PathProcessor processor) {
    myInitialCollection = collection;
    myWorkingCollection = new ArrayList<String>(myInitialCollection);
    myListModel = new DefaultListModel();
    myList.setModel(myListModel);

    // fill list
    reset();

    // listeners
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final FileChooserDescriptor dirChooser = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        dirChooser.setShowFileSystemRoots(true);
        dirChooser.setHideIgnored(true);
        dirChooser.setTitle(UIBundle.message("file.chooser.default.title"));
        FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(dirChooser, myContentPane);
        VirtualFile[] files = chooser.choose(null, null);
        for (VirtualFile file : files) {
        // adding to the end
          final String path = file.getPath();
          if (processor.addPath(myWorkingCollection, path)){
            myListModel.addElement(path);
          }
        }
      }
    });

    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int selected = myList.getSelectedIndex();
        if (selected != -1) {
          // removing index
          final String path = (String) myListModel.get(selected);
          if (processor.removePath(myWorkingCollection, path)){
            myListModel.remove(selected);
          }
        }
      }
    });
  }

  public JPanel getContentPane() {
    return myContentPane;
  }

  public List<String> getValues(){
    return myWorkingCollection;
  }

  public void reset() {
    myListModel.clear();
    myWorkingCollection = new ArrayList<String>(myInitialCollection);
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
