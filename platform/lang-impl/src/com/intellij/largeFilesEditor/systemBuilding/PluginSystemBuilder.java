// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.systemBuilding;

import com.intellij.largeFilesEditor.editor.EditorManager;
import com.intellij.largeFilesEditor.editor.EditorManagerImpl;
import com.intellij.largeFilesEditor.editor.RootComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class PluginSystemBuilder {
  private static final PluginSystemBuilder instance = new PluginSystemBuilder();

  public static PluginSystemBuilder getInstance() {
    return instance;
  }

    /*private PluginSystemBuilder() {

    }*/

  public RootComponent build(@NotNull Project project, @NotNull VirtualFile file) {
    EditorManager editorManager = new EditorManagerImpl(project, file);
    RootComponent rootComponent = new RootComponent(editorManager);
    return rootComponent;
  }
}
