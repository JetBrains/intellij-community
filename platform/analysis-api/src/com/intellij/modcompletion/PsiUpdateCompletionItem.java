// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcompletion;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNullByDefault;

/**
 * A {@link CompletionItem} that performs an update via {@link ModCommand#psiUpdate} API. The overwrite mode is handled automatically.
 */
@NotNullByDefault
public abstract class PsiUpdateCompletionItem implements CompletionItem {
  @Override
  public ModCommand perform(ActionContext actionContext, InsertionContext insertionContext) {
    return ModCommand.psiUpdate(actionContext, updater -> {
      if (insertionContext.mode() == InsertionMode.OVERWRITE) {
        updater.getDocument().deleteString(
          actionContext.offset(), calculateEndOffsetForOverwrite(updater.getDocument(), actionContext.offset()));
      }
      update(actionContext, insertionContext, updater);
    });
  }

  /**
   * @param document the document to use 
   * @param startFrom a caret position
   * @return the position of the identifier end. The default implementation uses Java identifier rules.
   * Override this method to use different rules for ID boundary.
   */
  protected int calculateEndOffsetForOverwrite(Document document, int startFrom) {
    final CharSequence text = document.getCharsSequence();
    int idEnd = startFrom;
    while (idEnd < text.length() && Character.isJavaIdentifierPart(text.charAt(idEnd))) {
      idEnd++;
    }
    return idEnd;
  }

  /**
   * Performs PSI/document update of the file copy to generate a final {@link ModCommand}. 
   * 
   * @param actionContext context of the action
   * @param insertionContext context of the insertion (like which character was used to finish the completion)
   * @param updater an updater to use
   */
  public abstract void update(ActionContext actionContext, InsertionContext insertionContext, ModPsiUpdater updater);
}
