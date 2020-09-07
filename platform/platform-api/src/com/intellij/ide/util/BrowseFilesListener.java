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
package com.intellij.ide.util;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 */
public class BrowseFilesListener implements ActionListener {
  public static final FileChooserDescriptor SINGLE_DIRECTORY_DESCRIPTOR = FileChooserDescriptorFactory.createSingleFolderDescriptor();
  public static final FileChooserDescriptor SINGLE_FILE_DESCRIPTOR = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();

  private final JTextField myTextField;
  private final @NlsContexts.DialogTitle String myTitle;
  private final @NlsContexts.Label String myDescription;
  protected final FileChooserDescriptor myChooserDescriptor;

  public BrowseFilesListener(JTextField textField,
                             @NlsContexts.DialogTitle String title,
                             @NlsContexts.Label String description,
                             FileChooserDescriptor chooserDescriptor) {
    myTextField = textField;
    myTitle = title;
    myDescription = description;
    myChooserDescriptor = chooserDescriptor;
  }

  @Nullable
  protected VirtualFile getFileToSelect() {
    final String path = myTextField.getText().trim().replace(File.separatorChar, '/');
    if (path.length() > 0) {
      File file = new File(path);
      while (file != null && !file.exists()) {
        file = file.getParentFile();
      }
      if (file != null) {
        return LocalFileSystem.getInstance().findFileByIoFile(file);
      }
    }
    return null;
  }

  protected void doSetText(@NotNull final String path) {
    myTextField.setText(path);
  }

  @Override
  public void actionPerformed(ActionEvent e ) {
    final VirtualFile fileToSelect = getFileToSelect();
    myChooserDescriptor.setTitle(myTitle); // important to set title and description here because a shared descriptor instance can be used
    myChooserDescriptor.setDescription(myDescription);
    FileChooser.chooseFiles(myChooserDescriptor, null, fileToSelect, files -> doSetText(FileUtil.toSystemDependentName(files.get(0).getPath())));
  }
}
