// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Registers {@link FileType} with specific file name extensions/patterns.
 *
 * @deprecated use {@code com.intellij.fileType} extension point instead for declarative registration
 */
@Deprecated
public abstract class FileTypeFactory {
  public static final ExtensionPointName<FileTypeFactory> FILE_TYPE_FACTORY_EP = new ExtensionPointName<>("com.intellij.fileTypeFactory");

  public abstract void createFileTypes(@NotNull FileTypeConsumer consumer);
}
