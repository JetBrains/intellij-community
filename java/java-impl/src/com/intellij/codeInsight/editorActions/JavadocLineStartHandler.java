/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavadocLineStartHandler extends EditorActionHandler {
  private static final String WHITESPACE = " \t";
  
  private final EditorActionHandler myOriginalHandler;

  public JavadocLineStartHandler(EditorActionHandler originalHandler) {
    super(true);
    myOriginalHandler = originalHandler;
  }

  @Override
  protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
    assert caret != null;
    Project project = editor.getProject();
    if (project != null && EditorSettingsExternalizable.getInstance().isSmartHome()) {
      Document document = editor.getDocument();
      CharSequence text = document.getImmutableCharSequence();
      int lineStartOffset = document.getLineStartOffset(caret.getLogicalPosition().line);
      int nonWsStartOffset = CharArrayUtil.shiftForward(text, lineStartOffset, WHITESPACE);
      if (CharArrayUtil.regionMatches(text, nonWsStartOffset, "/**") || CharArrayUtil.regionMatches(text, nonWsStartOffset, "*")) {
        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        PsiFile file = psiDocumentManager.getPsiFile(document);
        if (file instanceof PsiJavaFile) {
          psiDocumentManager.commitDocument(document);
          PsiElement startElement = file.findElementAt(nonWsStartOffset);
          if (startElement instanceof PsiDocToken) {
            IElementType type = ((PsiDocToken)startElement).getTokenType();
            if (type == JavaDocTokenType.DOC_COMMENT_START || type == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
              int targetOffset = CharArrayUtil.shiftForward(text, startElement.getTextRange().getEndOffset(), WHITESPACE);
              if (caret.getOffset() == targetOffset) targetOffset = lineStartOffset;
              caret.moveToOffset(targetOffset);
              caret.removeSelection();
              EditorModificationUtil.scrollToCaret(editor);
              return;
            }
          }
        }
      }
    }
    myOriginalHandler.execute(editor, caret, dataContext);    
  }
}
