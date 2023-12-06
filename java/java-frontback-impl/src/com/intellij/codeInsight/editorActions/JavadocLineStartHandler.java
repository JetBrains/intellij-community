// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavadocLineStartHandler extends EditorActionHandler.ForEachCaret {
  private static final String WHITESPACE = " \t";

  private final EditorActionHandler myOriginalHandler;
  private final boolean myWithSelection;

  public JavadocLineStartHandler(@NotNull EditorActionHandler originalHandler) {
    this(originalHandler, false);
  }

  public JavadocLineStartHandler(@NotNull EditorActionHandler originalHandler,
                                 boolean withSelection) {
    myOriginalHandler = originalHandler;
    myWithSelection = withSelection;
  }

  @Override
  protected void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    Project project = editor.getProject();
    if (project != null && EditorSettingsExternalizable.getInstance().isSmartHome()) {
      Document document = editor.getDocument();
      CharSequence text = document.getImmutableCharSequence();
      int lineStartOffset = document.getLineStartOffset(caret.getLogicalPosition().line);
      int nonWsStartOffset = CharArrayUtil.shiftForward(text, lineStartOffset, WHITESPACE);
      if (CharArrayUtil.regionMatches(text, nonWsStartOffset, "/**") || CharArrayUtil.regionMatches(text, nonWsStartOffset, "*")) {
        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        PsiFile file = psiDocumentManager.getPsiFile(document);
        if (file != null && isJavaFile(file)) {
          psiDocumentManager.commitDocument(document);
          PsiElement startElement = file.findElementAt(nonWsStartOffset);
          if (startElement == null || startElement.getNode() == null) {
            return;
          }
          IElementType type = startElement.getNode().getElementType();
          if (type == JavaDocTokenType.DOC_COMMENT_START || type == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
            int targetOffset = CharArrayUtil.shiftForward(text, startElement.getTextRange().getEndOffset(), WHITESPACE);
            if (caret.getOffset() == targetOffset) targetOffset = lineStartOffset;
            int selectionStartOffset = caret.getLeadSelectionOffset();
            caret.moveToOffset(targetOffset);
            if (myWithSelection) {
              caret.setSelection(selectionStartOffset, caret.getVisualPosition(), caret.getOffset());
            }
            else {
              caret.removeSelection();
            }
            EditorModificationUtilEx.scrollToCaret(editor);
            return;
          }
        }
      }
    }
    myOriginalHandler.execute(editor, caret, dataContext);
  }

  private static boolean isJavaFile(@Nullable PsiFile file){
    return file instanceof AbstractBasicJavaFile;
  }
}
