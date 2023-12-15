// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A command to update the references to the declaration if possible. A common use-case is to
 * invoke "change signature" refactoring automatically. May do nothing, if particular language,
 * or particular kind of declaration change is not supported. The command should follow actual
 * text update in the file via {@link ModUpdateFileText}.
 *
 * @implNote In IntelliJ IDEA interactive executor, it invokes the "suggested refactoring" mechanism.
 * 
 * @param file virtual file where the declaration resides
 * @param oldText the complete content of the virtual file before the declaration was updated
 * @param oldRange the complete text range of the declaration within the oldText
 * @param newRange the complete current text range of the declaration
 */
@ApiStatus.Experimental
public record ModUpdateReferences(@NotNull VirtualFile file, @NotNull String oldText, @NotNull TextRange oldRange,
                                  @NotNull TextRange newRange) implements ModCommand {
  /**
   * @param newRange updated new range
   * @return the equivalent command but with updated newRange.
   */
  public @NotNull ModUpdateReferences withNewRange(@NotNull TextRange newRange) {
    return newRange.equals(newRange()) ? this : new ModUpdateReferences(file, oldText, oldRange, newRange);
  }
}
