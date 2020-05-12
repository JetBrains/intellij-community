// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Experimental
public class BrowseFolderRunnable<T extends JComponent> implements Runnable {
  private   final String myTitle;
  private   final String myDescription;
  protected final TextComponentAccessor<? super T> myAccessor;
  protected final FileChooserDescriptor myFileChooserDescriptor;

  protected  T myTextComponent;
  private Project myProject;

  public BrowseFolderRunnable(@Nullable @NlsContexts.DialogTitle String title,
                              @Nullable @NlsContexts.Label String description,
                              @Nullable Project project,
                              FileChooserDescriptor fileChooserDescriptor,
                              @Nullable T component,
                              TextComponentAccessor<? super T> accessor) {
    if (fileChooserDescriptor != null && fileChooserDescriptor.isChooseMultiple()) {
      //LOG.error("multiple selection not supported");
      fileChooserDescriptor = new FileChooserDescriptor(fileChooserDescriptor) {
        @Override
        public boolean isChooseMultiple() {
          return false;
        }
      };
    }

    myTitle = title;
    myDescription = description;
    myTextComponent = component;
    myProject = project;
    myFileChooserDescriptor = fileChooserDescriptor;
    myAccessor = accessor;
  }

  @Nullable
  protected Project getProject() {
    return myProject;
  }

  protected void setProject(@Nullable Project project) {
    myProject = project;
  }

  @Override
  public void run() {
    FileChooserDescriptor fileChooserDescriptor = myFileChooserDescriptor;
    if (myTitle != null || myDescription != null) {
      fileChooserDescriptor = (FileChooserDescriptor)myFileChooserDescriptor.clone();
      if (myTitle != null) {
        fileChooserDescriptor.setTitle(myTitle);
      }
      if (myDescription != null) {
        fileChooserDescriptor.setDescription(myDescription);
      }
    }

    FileChooser.chooseFile(fileChooserDescriptor, getProject(), myTextComponent, getInitialFile(), this::onFileChosen);
  }

  @Nullable
  protected VirtualFile getInitialFile() {
    String directoryName = myAccessor.getText(myTextComponent).trim();
    if (StringUtil.isEmptyOrSpaces(directoryName)) {
      return null;
    }

    directoryName = FileUtil.toSystemIndependentName(directoryName);
    VirtualFile path = LocalFileSystem.getInstance().findFileByPath(expandPath(directoryName));
    while (path == null && directoryName.length() > 0) {
      int pos = directoryName.lastIndexOf('/');
      if (pos <= 0) break;
      directoryName = directoryName.substring(0, pos);
      path = LocalFileSystem.getInstance().findFileByPath(directoryName);
    }
    return path;
  }

  @NotNull
  protected String expandPath(@NotNull String path) {
    return path;
  }

  @NotNull
  protected String chosenFileToResultingText(@NotNull VirtualFile chosenFile) {
    return chosenFile.getPresentableUrl();
  }

  protected String getComponentText() {
    return myAccessor.getText(myTextComponent).trim();
  }

  protected void onFileChosen(@NotNull VirtualFile chosenFile) {
    myAccessor.setText(myTextComponent, chosenFileToResultingText(chosenFile));
  }
}
