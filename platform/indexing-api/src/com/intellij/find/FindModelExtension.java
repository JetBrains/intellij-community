// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface FindModelExtension {
  ExtensionPointName<FindModelExtension> EP_NAME = ExtensionPointName.create("com.intellij.findModelExtension");

  boolean iterateAdditionalFiles(@NotNull FindModel findModel, @NotNull Project project, @NotNull Processor<VirtualFile> consumer);
}
