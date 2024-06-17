// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Allows enabling refactoring inlays for a given language, instead of a gutter icon.
 * <p/>
 * Register in {@code com.intellij.refactoring.codeVisionSupport} extension point.
 */
public abstract class RefactoringCodeVisionSupport {
  public static final ExtensionPointName<RefactoringCodeVisionSupport> EP_NAME =
    ExtensionPointName.create("com.intellij.refactoring.codeVisionSupport");

  /**
   * @return true if rename inlay hints should be shown for the file type
   */
  public abstract boolean supportsRename(@NotNull FileType fileType);

  public abstract boolean supportsChangeSignature(@NotNull FileType fileType);

  public static boolean isRenameCodeVisionEnabled(@NotNull FileType fileType) {
    return ContainerUtil.exists(EP_NAME.getExtensionList(), extension -> extension.supportsRename(fileType));
  }

  public static boolean isChangeSignatureCodeVisionEnabled(@NotNull FileType fileType) {
    return ContainerUtil.exists(EP_NAME.getExtensionList(), extension -> extension.supportsChangeSignature(fileType));
  }
}
