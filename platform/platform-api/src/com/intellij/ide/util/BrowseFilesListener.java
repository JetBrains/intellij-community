// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class BrowseFilesListener implements ActionListener {
  /** @deprecated mutable object; use {@link FileChooserDescriptorFactory#createSingleFolderDescriptor} instead */
  @Deprecated(forRemoval = true)
  public static final FileChooserDescriptor SINGLE_DIRECTORY_DESCRIPTOR = FileChooserDescriptorFactory.createSingleFolderDescriptor();

  /** @deprecated mutable object; use {@link FileChooserDescriptorFactory#createSingleFileNoJarsDescriptor} instead */
  @Deprecated(forRemoval = true)
  public static final FileChooserDescriptor SINGLE_FILE_DESCRIPTOR = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();

  private final JTextField myTextField;
  private final @NlsContexts.DialogTitle String myTitle;
  private final @NlsContexts.Label String myDescription;
  protected final FileChooserDescriptor myChooserDescriptor;

  /**
   * @deprecated use {@link BrowseFilesListener#BrowseFilesListener(JTextField, FileChooserDescriptor)}
   * together with {@link FileChooserDescriptor#withTitle} and {@link FileChooserDescriptor#withDescription}
   */
  @Deprecated(forRemoval = true)
  public BrowseFilesListener(
    JTextField textField,
    @NlsContexts.DialogTitle String title,
    @NlsContexts.Label String description,
    FileChooserDescriptor chooserDescriptor
  ) {
    myTextField = textField;
    myTitle = title;
    myDescription = description;
    myChooserDescriptor = chooserDescriptor;
  }

  public BrowseFilesListener(JTextField textField, FileChooserDescriptor chooserDescriptor) {
    myTextField = textField;
    myTitle = null;
    myDescription = null;
    myChooserDescriptor = chooserDescriptor;
  }

  protected @Nullable VirtualFile getFileToSelect() {
    var path = myTextField.getText().trim().replace(File.separatorChar, '/');
    if (!path.isEmpty()) {
      var file = new File(path);
      while (file != null && !file.exists()) {
        file = file.getParentFile();
      }
      if (file != null) {
        return LocalFileSystem.getInstance().findFileByIoFile(file);
      }
    }
    return null;
  }

  protected void doSetText(final @NotNull String path) {
    myTextField.setText(path);
  }

  @Override
  public void actionPerformed(ActionEvent e ) {
    var fileToSelect = getFileToSelect();
    if (myTitle != null) {
      myChooserDescriptor.setTitle(myTitle);
    }
    if (myDescription != null) {
      myChooserDescriptor.setDescription(myDescription);
    }
    FileChooser.chooseFiles(myChooserDescriptor, null, fileToSelect, files -> doSetText(FileUtil.toSystemDependentName(files.get(0).getPath())));
  }
}
