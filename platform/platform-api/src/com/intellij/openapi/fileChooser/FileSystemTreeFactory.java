// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;


public interface FileSystemTreeFactory {

  FileSystemTree createFileSystemTree(Project project, FileChooserDescriptor fileChooserDescriptor);

  DefaultActionGroup createDefaultFileSystemActions(FileSystemTree fileSystemTree);

  final class SERVICE {
    private SERVICE() {
    }

    public static FileSystemTreeFactory getInstance() {
      return ServiceManager.getService(FileSystemTreeFactory.class);
    }
  }
}
