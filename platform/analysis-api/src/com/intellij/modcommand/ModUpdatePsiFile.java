// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * A command that updates the content of a given {@link PsiFile}
 * 
 * @param file file to update
 * @param oldText old text (expected). The command aborts if the old text doesn't match
 * @param newText new text
 */
public record ModUpdatePsiFile(@NotNull PsiFile file, @NotNull String oldText, @NotNull String newText) implements ModCommand {
  @Override
  public boolean isEmpty() {
    return oldText.equals(newText);
  }

  @Override
  public @NotNull Set<@NotNull PsiFile> modifiedFiles() {
    return Set.of(file);
  }

  @Override
  public @Nullable ModCommand tryMerge(@NotNull ModCommand next) {
    if (next instanceof ModUpdatePsiFile update && file.isEquivalentTo(update.file) &&
        newText.equals(update.oldText)) {
      return new ModUpdatePsiFile(file, oldText, update.newText);
    }
    return null;
  }
}
