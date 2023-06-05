// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * A command that updates the content of a given {@link PsiFile}
 * 
 * @param file file to update
 * @param oldText old text (expected). The command aborts if the old text doesn't match
 * @param newText new text
 * @param updatedRanges ranges in the old text that should be updated; empty list to calculate automatically
 */
public record ModUpdateFileText(@NotNull VirtualFile file, @NotNull String oldText, @NotNull String newText,
                                @NotNull List<@NotNull Fragment> updatedRanges) implements ModCommand {
  public ModUpdateFileText {
    for (int i = 0; i < updatedRanges.size(); i++) {
      Fragment prev = updatedRanges.get(i);
      if (prev.offset < 0 || prev.oldLength < 0 || prev.newLength < 0 || prev.offset() + prev.oldLength > oldText.length()) {
        throw new IllegalArgumentException("Range out of bounds: " + prev);
      }
      if (i < updatedRanges.size() - 1) {
        Fragment next = updatedRanges.get(i + 1);
        if (next.offset() <= prev.offset() + prev.oldLength()) {
          throw new IllegalArgumentException("Invalid ranges: " + updatedRanges);
        } 
      }
    }
  }
  
  @Override
  public boolean isEmpty() {
    return oldText.equals(newText);
  }

  @Override
  public @NotNull Set<@NotNull VirtualFile> modifiedFiles() {
    return Set.of(file);
  }

  /**
   * A fragment of the old text to update.
   * 
   * @param offset offset inside the original text
   * @param oldLength length of the old fragment
   * @param newLength length of the new fragment
   */
  public record Fragment(int offset, int oldLength, int newLength) {}
}
