// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides access to the base compiler output setting for a project ('Project compiler output' field in File | Project Structure | Project).
 */
public abstract class CompilerProjectExtension {
  public static @Nullable CompilerProjectExtension getInstance(@NotNull Project project) {
    return project.getService(CompilerProjectExtension.class);
  }

  @Nullable
  public abstract VirtualFile getCompilerOutput();

  @Nullable
  public abstract String getCompilerOutputUrl();

  public abstract VirtualFilePointer getCompilerOutputPointer();

  public abstract void setCompilerOutputPointer(VirtualFilePointer pointer);

  public abstract void setCompilerOutputUrl(String compilerOutputUrl);
}