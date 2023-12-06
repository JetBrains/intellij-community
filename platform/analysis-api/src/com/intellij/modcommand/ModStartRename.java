// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A command to invoke symbol rename UI. Ignored in batch mode.
 *
 * @param file            file with symbol location
 * @param symbolRange     symbol range within the file
 * @param nameSuggestions names to suggest. Execution engine is free to suggest other names as well.
 */
public record ModStartRename(@NotNull VirtualFile file, @NotNull RenameSymbolRange symbolRange, @NotNull List<String> nameSuggestions)
  implements ModCommand {

  /**
   * @param range new symbol range
   * @return the same command but with updated range
   */
  public @NotNull ModStartRename withRange(@NotNull RenameSymbolRange range) {
    return range.equals(symbolRange) ? this : new ModStartRename(file, range, nameSuggestions);
  }

  /**
   * @param range               whole symbol's range
   * @param nameIdentifierRange symbol's name identifier range
   */
  public record RenameSymbolRange(@NotNull TextRange range, @Nullable TextRange nameIdentifierRange) {
  }
}
