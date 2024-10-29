// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Experimental
public class BrowseFolderRunnable<T extends JComponent> implements Runnable {
  private final Project myProject;
  protected final TextComponentAccessor<? super T> myAccessor;
  protected final FileChooserDescriptor myFileChooserDescriptor;
  protected T myTextComponent;

  public BrowseFolderRunnable(
    @Nullable Project project,
    @NotNull FileChooserDescriptor fileChooserDescriptor,
    @Nullable T component,
    @NotNull TextComponentAccessor<? super T> accessor
  ) {
    if (fileChooserDescriptor.isChooseMultiple()) {
      Logger.getInstance(BrowseFolderRunnable.class).warn("multiple selection not supported");
    }
    myTextComponent = component;
    myProject = project;
    myFileChooserDescriptor = fileChooserDescriptor;
    myAccessor = accessor;
  }

  /**
   * @deprecated use {@link #BrowseFolderRunnable(Project, FileChooserDescriptor, JComponent, TextComponentAccessor)}
   * together with {@link FileChooserDescriptor#withTitle} and {@link FileChooserDescriptor#withDescription}
   */
  @Deprecated(forRemoval = true)
  public BrowseFolderRunnable(
    @Nullable @NlsContexts.DialogTitle String title,
    @Nullable @NlsContexts.Label String description,
    @Nullable Project project,
    @NotNull FileChooserDescriptor fileChooserDescriptor,
    @Nullable T component,
    @NotNull TextComponentAccessor<? super T> accessor
  ) {
    if (fileChooserDescriptor.isChooseMultiple()) {
      Logger.getInstance(BrowseFolderRunnable.class).error("multiple selection not supported");
    }
    if (title != null) {
      fileChooserDescriptor = fileChooserDescriptor.withTitle(title);
    }
    if (description != null) {
      fileChooserDescriptor = fileChooserDescriptor.withDescription(description);
    }
    myTextComponent = component;
    myProject = project;
    myFileChooserDescriptor = fileChooserDescriptor;
    myAccessor = accessor;
  }

  protected final @Nullable Project getProject() {
    return myProject;
  }

  @Override
  public void run() {
    chooseFile(myFileChooserDescriptor);
  }

  protected void chooseFile(FileChooserDescriptor descriptor) {
    FileChooser.chooseFile(descriptor, getProject(), myTextComponent, getInitialFile(), this::onFileChosen);
  }

  protected @Nullable VirtualFile getInitialFile() {
    var directoryName = myAccessor.getText(myTextComponent).trim();
    if (directoryName.isBlank()) return null;

    var path = NioFiles.toPath(expandPath(directoryName));
    if (path == null || !path.isAbsolute()) return null;

    while (path != null) {
      var result = LocalFileSystem.getInstance().findFileByNioFile(path);
      if (result != null) return result;
      path = path.getParent();
    }
    return null;
  }

  protected @NotNull @NonNls String expandPath(@NotNull String path) {
    var descriptor = BrowseFolderDescriptor.asBrowseFolderDescriptor(myFileChooserDescriptor);
    var convertTextToPath = descriptor.getConvertTextToPath();
    return convertTextToPath != null ? convertTextToPath.invoke(path) : path;
  }

  protected @NotNull @NlsSafe String chosenFileToResultingText(@NotNull VirtualFile chosenFile) {
    var descriptor = BrowseFolderDescriptor.asBrowseFolderDescriptor(myFileChooserDescriptor);
    var convertFileToText = descriptor.getConvertFileToText();
    if (convertFileToText != null) {
      return convertFileToText.invoke(chosenFile);
    }
    var convertPathToText = descriptor.getConvertPathToText();
    if (convertPathToText != null) {
      return convertPathToText.invoke(chosenFile.getPath());
    }
    return chosenFile.getPresentableUrl();
  }

  protected String getComponentText() {
    return myAccessor.getText(myTextComponent).trim();
  }

  protected void onFileChosen(@NotNull VirtualFile chosenFile) {
    myAccessor.setText(myTextComponent, chosenFileToResultingText(chosenFile));
  }
}
