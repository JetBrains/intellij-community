package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;

public class FinalListener {
  private final Editor myEditor;
  private static final Logger LOG = Logger.getInstance(FinalListener.class);

  public FinalListener(Editor editor) {
    myEditor = editor;
  }

  public void perform(final boolean generateFinal, PsiVariable variable) {
    perform(generateFinal, PsiModifier.FINAL, variable);
  }

  public void perform(final boolean generateFinal, final String modifier, final PsiVariable variable) {
    final Document document = myEditor.getDocument();
    LOG.assertTrue(variable != null);
    final PsiModifierList modifierList = variable.getModifierList();
    LOG.assertTrue(modifierList != null);
    final int textOffset = modifierList.getTextOffset();

    final Runnable runnable = () -> {
      if (generateFinal) {
        final PsiTypeElement typeElement = variable.getTypeElement();
        final int typeOffset = typeElement != null ? typeElement.getTextOffset() : textOffset;
        document.insertString(typeOffset, modifier + " ");
      }
      else {
        final int idx = modifierList.getText().indexOf(modifier);
        document.deleteString(textOffset + idx, textOffset + idx + modifier.length() + 1);
      }
    };
    final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
    if (lookup != null) {
      lookup.performGuardedChange(runnable);
    } else {
      runnable.run();
    }
    PsiDocumentManager.getInstance(variable.getProject()).commitDocument(document);
  }
}
