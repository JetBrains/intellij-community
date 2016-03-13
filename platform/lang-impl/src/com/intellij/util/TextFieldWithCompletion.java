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
package com.intellij.util;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorCustomization;
import com.intellij.ui.LanguageTextField;
import org.jetbrains.annotations.NotNull;

public class TextFieldWithCompletion extends LanguageTextField {
  private final boolean myAutoPopup;

  public TextFieldWithCompletion(@NotNull Project project,
                                 @NotNull TextCompletionProvider provider,
                                 @NotNull String value,
                                 boolean oneLineMode,
                                 boolean autoPopup) {
    super(PlainTextLanguage.INSTANCE, project, value, new TextCompletionUtil.DocumentWithCompletionCreator(provider), oneLineMode);
    myAutoPopup = autoPopup;
  }

  @Override
  protected EditorEx createEditor() {
    EditorEx editor = super.createEditor();
    EditorCustomization disableSpellChecking = SpellCheckingEditorCustomizationProvider.getInstance().getDisabledCustomization();
    if (disableSpellChecking != null) disableSpellChecking.customize(editor);
    editor.putUserData(AutoPopupController.ALWAYS_AUTO_POPUP, myAutoPopup);
    return editor;
  }
}
