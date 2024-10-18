// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * A command that updates the content of a given {@link PsiFile}.
 * 
 * @param file file to update
 * @param oldText old text (expected). The command aborts if the old text doesn't match
 * @param newText new text
 * @param updatedRanges ranges in the text that should be updated, sorted in ascending order; 
 *                      use an empty list to calculate automatically
 */
public record ModUpdateFileText(@NotNull VirtualFile file, @NotNull String oldText, @NotNull String newText,
                                @NotNull List<@NotNull Fragment> updatedRanges) implements ModCommand {
  public ModUpdateFileText {
    for (int i = 0; i < updatedRanges.size(); i++) {
      Fragment prev = updatedRanges.get(i);
      if (prev.offset() + prev.newLength > newText.length()) {
        throw new IllegalArgumentException("Range out of bounds: " + prev + "; newText.length()=" + newText.length());
      }
      if (i < updatedRanges.size() - 1) {
        Fragment next = updatedRanges.get(i + 1);
        if (next.offset() <= prev.offset() + prev.newLength()) {
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
   * @param offset offset within the {@link #file()} before this command is applied
   * @return new offset after this command is applied
   */
  int translateOffset(int offset, boolean leanRight) {
    for (Fragment range : updatedRanges) {
      offset = leanRight ? range.adjustForwardLeanRight(offset) : range.adjustForward(offset);
    }
    return offset;
  }

  /**
   * A fragment of the text to update.
   *
   * @param offset the start offset inside the new text
   * @param oldLength length of the old fragment
   * @param newLength length of the new fragment
   */
  public record Fragment(int offset, int oldLength, int newLength) {
    public Fragment {
      if (offset < 0) throw new IllegalArgumentException("Negative offset");
      if (oldLength < 0) throw new IllegalArgumentException("Negative oldLength");
      if (newLength < 0) throw new IllegalArgumentException("Negative newLength");
    }

    public @NotNull Fragment shift(int diff) {
      return diff == 0 ? this : new Fragment(offset + diff, oldLength, newLength);
    }

    public boolean intersects(@NotNull Fragment other) {
      return Math.max(offset, other.offset) <= Math.min(offset + newLength, other.offset + other.oldLength);
    }

    private int adjustForward(int pos) {
      if (pos <= offset) return pos;
      if (pos <= offset + oldLength) return offset;
      return pos - oldLength + newLength;
    }

    private int adjustForwardLeanRight(int pos) {
      if (pos < offset) return pos;
      if (pos <= offset + oldLength) return offset + newLength;
      return pos - oldLength + newLength;
    }

    private int adjustBackward(int pos) {
      if (pos <= offset) return pos;
      if (pos <= offset + newLength) return offset;
      return pos - newLength + oldLength;
    }

    public @NotNull Fragment mergeWithNext(@NotNull Fragment next) {
      int newStartOffset = Math.min(offset, next.offset);
      int newOrigEndOffset = Math.max(offset + oldLength, adjustBackward(next.offset + next.oldLength));
      int newUpdatedEndOffset = Math.max(next.adjustForward(offset + newLength), next.offset + next.newLength);
      return new Fragment(newStartOffset, newOrigEndOffset - newStartOffset, newUpdatedEndOffset - newStartOffset);
    }
  }
}
