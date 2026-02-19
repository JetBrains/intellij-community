// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.file;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FileLookupInfoProvider {
  public static final ExtensionPointName<FileLookupInfoProvider> EP_NAME = ExtensionPointName.create("com.intellij.fileLookupInfoProvider");

  public abstract FileType @NotNull [] getFileTypes();

  public abstract @Nullable Pair<String, String> getLookupInfo(final @NotNull VirtualFile file, Project project);
}
