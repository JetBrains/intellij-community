/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  private final Editor myEditor;
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
