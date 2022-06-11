// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Additional interface which optionally may be implemented by {@link FileType} to control how files are associated
 * with the IDE in the operating system.
 */
public interface OSFileIdeAssociation {
  enum ExtensionMode {
    /**
     * Let the IDE choose if only selected or all extensions will be used to associate files with the IDE.
     */
    Default,
    /**
     * Allow a user to choose extensions of files to be opened with the IDE.
     */
    Selected,
    /**
     * Use all available extensions to associate files with the IDE.
     */
    All
  }

  /**
   * @return One of:
   * <ul>
   *   <li>{@link ExtensionMode#Default}</li>
   *   <li>{@link ExtensionMode#Selected}</li>
   *   <li>{@link ExtensionMode#All}</li>
   * </ul>
   */
  default @NotNull ExtensionMode getExtensionMode() {
    return ExtensionMode.All;
  }

  /** @deprecated please implement {@link #getExtensionMode()} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @SuppressWarnings("IdentifierGrammar")
  default ExtensionMode getExtensionsMode() {
    return getExtensionMode();
  }

  default boolean isFileAssociationAllowed() {
    return true;
  }
}
