/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;

public class FileEditorRule implements GetDataRule {
  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    final Editor editor = (Editor)dataProvider.getData(CommonDataKeys.EDITOR.getName());
    if (editor == null || editor.isDisposed()) {
      return null;
    }

    final Boolean aBoolean = editor.getUserData(EditorTextField.SUPPLEMENTARY_KEY);
    if (aBoolean != null && aBoolean.booleanValue()) {
      return null;
    }

    return TextEditorProvider.getInstance().getTextEditor(editor);
  }
}
