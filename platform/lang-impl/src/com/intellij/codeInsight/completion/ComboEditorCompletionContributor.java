// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.StringComboboxEditor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Internal
public final class ComboEditorCompletionContributor extends CompletionContributor implements DumbAware {

  public static final Key<Boolean> CONTINUE_RUN_COMPLETION = Key.create("CONTINUE_RUN_COMPLETION");
  private static final Key<LookupElementProvider> LOOKUP_ELEMENT_PROVIDER_KEY = Key.create("LOOKUP_ELEMENT_PROVIDER_KEY");

  @Override
  public void fillCompletionVariants(final @NotNull CompletionParameters parameters, final @NotNull CompletionResultSet result) {
    if (parameters.getInvocationCount() == 0) {
      return;
    }

    final PsiFile file = parameters.getOriginalFile();
    final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document != null) {
      JComboBox comboBox = document.getUserData(StringComboboxEditor.COMBO_BOX_KEY);
      if (comboBox != null) {
        String substring = document.getText().substring(0, parameters.getOffset());
        boolean plainPrefixMatcher = Boolean.TRUE.equals(document.getUserData(StringComboboxEditor.USE_PLAIN_PREFIX_MATCHER));
        final CompletionResultSet resultSet = plainPrefixMatcher ?
                                              result.withPrefixMatcher(new PlainPrefixMatcher(substring)) :
                                              result.withPrefixMatcher(substring);
        final LookupElementProvider lookupElementProvider = LOOKUP_ELEMENT_PROVIDER_KEY.get(document, LookupElementProvider.DEFAULT);
        final int count = comboBox.getItemCount();
        for (int i = 0; i < count; i++) {
          final Object o = comboBox.getItemAt(i);
          if (o instanceof String) {
            resultSet.addElement(PrioritizedLookupElement.withPriority(lookupElementProvider.createLookupElement((String)o), count - i));
          }
        }
        if (!Boolean.TRUE.equals(document.getUserData(CONTINUE_RUN_COMPLETION))) {
          result.stopHere();
        }
      }
    }
  }

  public static void installLookupElementProvider(final @NotNull Document document, final @NotNull LookupElementProvider provider) {
    LOOKUP_ELEMENT_PROVIDER_KEY.set(document, provider);
  }

  public interface LookupElementProvider {
    DefaultLookupElementProvider DEFAULT = new DefaultLookupElementProvider();
    @NotNull
    LookupElement createLookupElement(@NotNull String lookupString);
  }

  public static final class DefaultLookupElementProvider implements LookupElementProvider {
    @Override
    public @NotNull LookupElementBuilder createLookupElement(@NotNull String lookupString) {
      return LookupElementBuilder.create(lookupString).withInsertHandler((context, item) -> {
        final Document document = context.getEditor().getDocument();
        document.deleteString(context.getEditor().getCaretModel().getOffset(), document.getTextLength());
      });
    }
  }
}
