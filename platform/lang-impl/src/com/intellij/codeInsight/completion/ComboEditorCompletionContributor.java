/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.StringComboboxEditor;

import javax.swing.*;

/**
 * @author peter
 */
public class ComboEditorCompletionContributor extends CompletionContributor{

  @Override
  public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    final PsiFile file = parameters.getOriginalFile();
    final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document != null) {
      final JComboBox comboBox = document.getUserData(StringComboboxEditor.COMBO_BOX_KEY);
      if (comboBox != null) {
        final CompletionResultSet resultSet = result.withPrefixMatcher(document.getText().substring(0, parameters.getOffset()));
        final int count = comboBox.getItemCount();
        for (int i = 0; i < count; i++) {
          final Object o = comboBox.getItemAt(i);
          if (o instanceof String) {
            resultSet.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create((String)o).setInsertHandler(new InsertHandler<LookupElement>() {
              public void handleInsert(final InsertionContext context, final LookupElement item) {
                final Document document = context.getEditor().getDocument();
                document.deleteString(context.getEditor().getCaretModel().getOffset(), document.getTextLength());
              }
            }), count-i));
          }
        }
        result.stopHere();
      }
    }
  }
}
