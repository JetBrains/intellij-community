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
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicElementTypes;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.impl.source.BasicJavaTokenSet;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.BasicJavaDocElementType.DOC_COMMENT;

public class CommentBreakerEnterProcessor implements ASTNodeEnterProcessor {

  private final BasicJavaTokenSet myCommentTypes = BasicJavaTokenSet.orSet(
    BasicElementTypes.JAVA_PLAIN_COMMENT_BIT_SET, BasicJavaTokenSet.create(DOC_COMMENT)
  );

  @Override
  public boolean doEnter(@NotNull Editor editor, @NotNull ASTNode astNode, boolean isModified) {
    PsiElement psiElement = BasicJavaAstTreeUtil.toPsi(astNode);
    if (isModified || psiElement == null) return false;
    final PsiElement atCaret = psiElement.getContainingFile().findElementAt(editor.getCaretModel().getOffset());
    if (atCaret == null) return false;
    final ASTNode comment = BasicJavaAstTreeUtil.getParentOfType(atCaret.getNode(), myCommentTypes, false);
    if (comment != null) {
      plainEnter(editor);
      if (BasicJavaAstTreeUtil.is(comment, JavaTokenType.END_OF_LINE_COMMENT)) {
        EditorModificationUtilEx.insertStringAtCaret(editor, "// ");
      }
      return true;
    }
    return false;
  }

  private static void plainEnter(Editor editor) {
    getEnterHandler().execute(editor, editor.getCaretModel().getCurrentCaret(), EditorUtil.getEditorDataContext(editor));
  }

  private static EditorActionHandler getEnterHandler() {
    return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE);
  }
}
