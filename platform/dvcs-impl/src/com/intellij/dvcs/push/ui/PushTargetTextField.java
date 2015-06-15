/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.dvcs.push.ui;

import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorCustomization;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;

public class PushTargetTextField extends TextFieldWithAutoCompletion<String> {

  public PushTargetTextField(@NotNull Project project, @NotNull final List<String> targetVariants, @NotNull String defaultTargetName) {
    super(project, getCompletionProvider(targetVariants), true, defaultTargetName);
    setOneLineMode(true);
    addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        selectAll();
      }

      @Override
      public void focusLost(FocusEvent e) {
        removeSelection();
      }
    });
  }

  @Override
  protected EditorEx createEditor() {
    // editor created lazy, so we need to update editor customization after initialization
    EditorEx editorEx = super.createEditor();
    EditorCustomization customization = SpellCheckingEditorCustomizationProvider.getInstance().getDisabledCustomization();
    if (customization != null) {
      customization.customize(editorEx);
    }
    return editorEx;
  }

  @Override
  protected void updateBorder(@NotNull final EditorEx editor) {
  }

  @NotNull
  private static TextFieldWithAutoCompletionListProvider<String> getCompletionProvider(@NotNull final List<String> targetVariants) {
    return new StringsCompletionProvider(targetVariants, null) {
      @Override
      public int compare(String item1, String item2) {
        return Integer.valueOf(ContainerUtil.indexOf(targetVariants, item1)).compareTo(ContainerUtil.indexOf(targetVariants, item2));
      }
    };
  }
}
