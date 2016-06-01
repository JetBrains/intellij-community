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
package com.intellij.codeInsight.hint;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Disposer;

public class InlayHidingEscapeHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public InlayHidingEscapeHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  @Override
  public void doExecute(Editor editor, Caret caret, DataContext dataContext) {
    for (Inlay inlay : editor.getInlayModel().getElementsInRange(0, editor.getDocument().getTextLength() + 1, Inlay.Type.BLOCK)) {
      Disposer.dispose(inlay);
    }
    myOriginalHandler.execute(editor, caret, dataContext);
  }
}
