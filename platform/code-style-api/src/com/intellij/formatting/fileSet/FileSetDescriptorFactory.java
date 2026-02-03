// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.fileSet;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FileSetDescriptorFactory {

  ExtensionPointName<FileSetDescriptorFactory> EP_NAME = new ExtensionPointName<>("com.intellij.fileSetDescriptorFactory");

  @Nullable
  FileSetDescriptor createDescriptor(@NotNull FileSetDescriptor.State state);

}
