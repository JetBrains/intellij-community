package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiVariable;

/**
* User: anna
*/
public abstract class VisibilityListener {
  private Editor myEditor;
  private static final Logger LOG = Logger.getInstance("#" + VisibilityListener.class.getName());

  protected VisibilityListener(Editor editor) {
    myEditor = editor;
  }

  /**
   * to be performed in write action
   */
  public void perform(final PsiVariable variable) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    final Document document = myEditor.getDocument();
    LOG.assertTrue(variable != null);
    final PsiModifierList modifierList = variable.getModifierList();
    LOG.assertTrue(modifierList != null);
    int textOffset = modifierList.getTextOffset();
    final String modifierListText = modifierList.getText();

    int length = PsiModifier.PUBLIC.length();
    int idx = modifierListText.indexOf(PsiModifier.PUBLIC);

    if (idx == -1) {
      idx = modifierListText.indexOf(PsiModifier.PROTECTED);
      length = PsiModifier.PROTECTED.length();
    }

    if (idx == -1) {
      idx = modifierListText.indexOf(PsiModifier.PRIVATE);
      length = PsiModifier.PRIVATE.length();
    }

    String visibility = getVisibility();
    if (visibility == PsiModifier.PACKAGE_LOCAL) {
      visibility = "";
    }

    final boolean wasPackageLocal = idx == -1;
    final boolean isPackageLocal = visibility.isEmpty();

    final int startOffset = textOffset + (wasPackageLocal ? 0 : idx);
    final int endOffset;
    if (wasPackageLocal) {
      endOffset = startOffset;
    }
    else {
      endOffset = textOffset + length + (isPackageLocal ? 1 : 0);
    }

    final String finalVisibility = visibility + (wasPackageLocal ? " " : "");

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        document.replaceString(startOffset, endOffset, finalVisibility);
      }
    };

    final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
    if (lookup != null) {
      lookup.performGuardedChange(runnable);
    } else {
      runnable.run();
    }
  }

  protected abstract String getVisibility();
}
