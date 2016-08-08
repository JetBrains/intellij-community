/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.textCompletion;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LanguageTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TextCompletionUtil {
  public static final Key<TextCompletionProvider> COMPLETING_TEXT_FIELD_KEY = Key.create("COMPLETING_TEXT_FIELD_KEY");
  public static final Key<Boolean> AUTO_POPUP_KEY = Key.create("AUTOPOPUP_TEXT_FIELD_KEY");

  public static void installProvider(@NotNull PsiFile psiFile, @NotNull TextCompletionProvider provider, boolean autoPopup) {
    psiFile.putUserData(COMPLETING_TEXT_FIELD_KEY, provider);
    psiFile.putUserData(AUTO_POPUP_KEY, autoPopup);
  }

  @Nullable
  public static TextCompletionProvider getProvider(@NotNull PsiFile file) {
    TextCompletionProvider provider = file.getUserData(COMPLETING_TEXT_FIELD_KEY);

    if (provider == null || (DumbService.isDumb(file.getProject()) && !DumbService.isDumbAware(provider))) {
      return null;
    }
    return provider;
  }

  public static void installCompletionHint(@NotNull EditorEx editor) {
    String completionShortcutText =
      KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION));
    if (!StringUtil.isEmpty(completionShortcutText)) {

      final Ref<Boolean> toShowHintRef = new Ref<>(true);
      editor.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        public void documentChanged(DocumentEvent e) {
          toShowHintRef.set(false);
        }
      });

      editor.addFocusListener(new FocusChangeListener() {
        @Override
        public void focusGained(final Editor editor) {
          if (toShowHintRef.get() && editor.getDocument().getText().isEmpty()) {
            ApplicationManager.getApplication().invokeLater(
              () -> HintManager.getInstance().showInformationHint(editor, "Code completion available ( " + completionShortcutText + " )"));
          }
        }

        @Override
        public void focusLost(Editor editor) {
          // Do nothing
        }
      });
    }
  }

  public static class DocumentWithCompletionCreator extends LanguageTextField.SimpleDocumentCreator {
    @NotNull private final TextCompletionProvider myProvider;
    private final boolean myAutoPopup;

    public DocumentWithCompletionCreator(@NotNull TextCompletionProvider provider, boolean autoPopup) {
      myProvider = provider;
      myAutoPopup = autoPopup;
    }

    @Override
    public void customizePsiFile(@NotNull PsiFile file) {
      installProvider(file, myProvider, myAutoPopup);
    }
  }
}
