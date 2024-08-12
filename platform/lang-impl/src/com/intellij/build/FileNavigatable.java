// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public final class FileNavigatable implements Navigatable {
  private final Project myProject;
  private final NullableLazyValue<OpenFileDescriptor> myValue;
  private final FilePosition myFilePosition;

  public FileNavigatable(@NotNull Project project, @NotNull FilePosition filePosition) {
    myProject = project;
    myFilePosition = filePosition;
    myValue = new NullableLazyValue<>() {
      @Override
      protected @Nullable OpenFileDescriptor compute() {
        return createDescriptor();
      }
    };
  }

  @Override
  public void navigate(boolean requestFocus) {
    Navigatable descriptor = getFileDescriptor();
    if (descriptor != null) {
      descriptor.navigate(requestFocus);
    }
  }

  @Override
  public boolean canNavigate() {
    Navigatable descriptor = getFileDescriptor();
    if (descriptor != null) {
      return descriptor.canNavigate();
    }
    return false;
  }

  @Override
  public boolean canNavigateToSource() {
    Navigatable descriptor = getFileDescriptor();
    if (descriptor != null) {
      return descriptor.canNavigateToSource();
    }
    return false;
  }

  public @Nullable OpenFileDescriptor getFileDescriptor() {
    return myValue.getValue();
  }

  public @NotNull FilePosition getFilePosition() {
    return myFilePosition;
  }

  private @Nullable OpenFileDescriptor createDescriptor() {
    OpenFileDescriptor descriptor = null;
    VirtualFile file = VfsUtil.findFileByIoFile(myFilePosition.getFile(), false);
    if (file != null) {
      descriptor = new OpenFileDescriptor(myProject, file, myFilePosition.getStartLine(), myFilePosition.getStartColumn());
    }
    return descriptor;
  }
}
