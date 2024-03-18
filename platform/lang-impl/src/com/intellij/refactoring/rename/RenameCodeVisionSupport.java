// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Allows enabling the {@link RenameCodeVisionProvider} for a given language.
 * This will promote the rename refactoring with an inlay hint instead of a gutter icon.
 * <p/>
 * Register in {@code com.intellij.rename.codeVisionSupport} extension point.
 */
public abstract class RenameCodeVisionSupport {
  public static final ExtensionPointName<RenameCodeVisionSupport> EP_NAME =
    ExtensionPointName.create("com.intellij.refactoring.renameCodeVisionSupport");

  /**
   * @return true if rename inlay hints should be shown for the file type
   */
  public abstract boolean supports(@NotNull FileType fileType);

  public static boolean isEnabledFor(@NotNull FileType fileType) {
    return ContainerUtil.exists(EP_NAME.getExtensionList(), extension -> extension.supports(fileType));
  }
}
