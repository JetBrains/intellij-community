// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A command to invoke symbol rename UI. Ignored in batch mode.
 *
 * @param file            file with symbol location
 * @param symbolRange     symbol range within the file
 * @param nameSuggestions names to suggest. Execution engine is free to suggest other names as well.
 */
public record ModRenameSymbol(@NotNull VirtualFile file, @NotNull TextRange symbolRange, @NotNull List<String> nameSuggestions)
  implements ModCommand {

  /**
   * @param range new symbol range
   * @return the same command but with updated range
   */
  public @NotNull ModRenameSymbol withRange(@NotNull TextRange range) {
    return range.equals(symbolRange) ? this : new ModRenameSymbol(file, range, nameSuggestions);
  }
}
