// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.surroundWith;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModNavigate;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A surrounder that generates {@link ModCommand} instead of execution the action directly
 */
@ApiStatus.Experimental
public abstract class ModCommandSurrounder implements Surrounder {
  @Override
  public final boolean startInWriteAction() {
    return false;
  }

  /**
   * Generates the {@link ModCommand} for the <em>Code | Surround With</em> action on the specified range of elements.
   * <p>
   * @param elements the elements to be surrounded
   * @return ModCommand, which when executed, will perform the surrounding. It may also modify the caret position.
   */
  public abstract @NotNull ModCommand surroundElements(@NotNull ActionContext context, @NotNull PsiElement @NotNull [] elements);

  @Override
  public final @Nullable TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, PsiElement @NotNull [] elements) {
    if (elements.length == 0) {
      return null;
    }
    PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    Document document = editor.getDocument();
    PsiFile file = manager.getPsiFile(document);
    if (file == null) return null;
    WriteAction.run(() -> manager.doPostponedOperationsAndUnblockDocument(document));
    ActionContext context = ActionContext.from(editor, file);
    var commandSupplier = new Supplier<ModCommand>() {
      TextRange range;
      
      @Override
      public ModCommand get() {
        ModCommand command = ModCommandSurrounder.this.surroundElements(context, elements);
        ModNavigate navigate = ContainerUtil.findInstance(command.unpack(), ModNavigate.class);
        if (navigate != null) {
          range = TextRange.create(navigate.selectionStart(), navigate.selectionEnd());
        }
        return command;
      }
    };
    String title = getTemplateDescription();
    ModCommandExecutor.executeInteractively(context, title, editor, commandSupplier);
    return commandSupplier.range;
  }
}
