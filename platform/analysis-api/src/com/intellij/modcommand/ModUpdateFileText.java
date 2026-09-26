// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
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
  public int translateOffset(int offset, boolean leanRight) {
    for (Fragment range : updatedRanges) {
      offset = leanRight ? range.adjustForwardLeanRight(offset) : range.adjustForward(offset);
    }
    return offset;
  }

  /**
   * Returns equivalent {@link ModUpdateFileText} with shrinked fragments if they contained the same before/after text at their end.
   * <p>
   * For example, we had old text "example" and new text "bye" and 
   * a single fragment covering the whole text (offset = 0, oldLength = 7, newLength = 3).
   * The result of {@code shrinkFragments()} will be a fragment (offset = 0, oldLength = 6, newLength = 2), as 'e' at the end is not changed.
   * </p>
   * 
   * @return equivalent {@link ModUpdateFileText} with shrinked fragments.
   */
  public ModUpdateFileText shrinkFragments() {
    List<Fragment> changed = null;
    int diff = 0;
    for (Fragment range : updatedRanges) {
      int oldLen = range.oldLength;
      int newLen = range.newLength;
      while (oldLen > 0 && newLen > 0 && oldText.charAt(range.offset+diff+oldLen-1)==newText.charAt(range.offset+newLen-1)) {
        oldLen--;
        newLen--;
      }
      if (oldLen != range.oldLength) {
        if (changed == null) changed = new ArrayList<>();
        range = new Fragment(range.offset, oldLen, newLen);
      }
      if (changed != null) {
        changed.add(range);
      }
      diff += range.oldLength - range.newLength;
    }
    if (changed == null) return this;
    return new ModUpdateFileText(file, oldText, newText, ContainerUtil.concat(updatedRanges.subList(0, updatedRanges.size() - changed.size()), changed));
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
