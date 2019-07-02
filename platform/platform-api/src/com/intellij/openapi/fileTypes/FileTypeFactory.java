// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 * @deprecated use {@code com.intellij.fileType} extension point instead
 */
@Deprecated
public abstract class FileTypeFactory {
  public static final ExtensionPointName<FileTypeFactory> FILE_TYPE_FACTORY_EP = ExtensionPointName.create("com.intellij.fileTypeFactory");

  public abstract void createFileTypes(@NotNull FileTypeConsumer consumer);
}
