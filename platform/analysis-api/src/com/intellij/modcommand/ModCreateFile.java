// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import org.jetbrains.annotations.NotNull;

/**
 * @param file file to create
 * @param text file content
 */
public record ModCreateFile(@NotNull FutureVirtualFile file, @NotNull Content content) implements ModCommand {

  /**
   * New file content
   */
  public sealed interface Content {}

  /**
   * Text content
   * 
   * @param text text to write into the new file. The encoding will be selected automatically based on the IDE settings
   */
  public record Text(@NotNull String text) implements Content {}

  /**
   * Binary content
   * 
   * @param bytes bytes to write into the new file.
   */
  public record Binary(byte @NotNull [] bytes) implements Content {}
}
