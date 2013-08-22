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
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.JavaClassReferenceCompletionContributor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class JavaTypedHandler extends TypedHandlerDelegate {
  static final TokenSet INVALID_INSIDE_REFERENCE = TokenSet.create(JavaTokenType.SEMICOLON, JavaTokenType.LBRACE, JavaTokenType.RBRACE);
  private boolean myJavaLTTyped;

  private static void autoPopupMemberLookup(Project project, final Editor editor) {
    AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, new Condition<PsiFile>() {
      @Override
      public boolean value(final PsiFile file) {
        int offset = editor.getCaretModel().getOffset();

        PsiElement lastElement = file.findElementAt(offset - 1);
        if (lastElement == null) {
          return false;
        }

        //do not show lookup when typing varargs ellipsis
        final PsiElement prevSibling = PsiTreeUtil.prevVisibleLeaf(lastElement);
        if (prevSibling == null || ".".equals(prevSibling.getText())) return false;
        PsiElement parent = prevSibling;
        do {
          parent = parent.getParent();
        } while(parent instanceof PsiJavaCodeReferenceElement || parent instanceof PsiTypeElement);
        if (parent instanceof PsiParameterList || parent instanceof PsiParameter) return false;

        if (!".".equals(lastElement.getText()) && !"#".equals(lastElement.getText())) {
          return JavaClassReferenceCompletionContributor.findJavaClassReference(file, offset - 1) != null;
        }
        else{
          final PsiElement element = file.findElementAt(offset);
          return element == null ||
                 !"#".equals(lastElement.getText()) ||
                 PsiTreeUtil.getParentOfType(element, PsiDocComment.class) != null;
        }
      }
    });
  }

  @Override
  public Result beforeCharTyped(final char c, final Project project, final Editor editor, final PsiFile file, final FileType fileType) {
    if (c == '@' && file instanceof PsiJavaFile) {
      autoPopupJavadocLookup(project, editor);
    }
    else if (c == '#' || c == '.') {
      autoPopupMemberLookup(project, editor);
    }


    final FileType originalFileType = getOriginalFileType(file);

    int offsetBefore = editor.getCaretModel().getOffset();

    //important to calculate before inserting charTyped
    myJavaLTTyped = '<' == c &&
                    file instanceof PsiJavaFile &&
                    !(file instanceof JspFile) &&
                    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
                    PsiUtil.isLanguageLevel5OrHigher(file) &&
                    isAfterClassLikeIdentifierOrDot(offsetBefore, editor);

    if ('>' == c) {
      if (file instanceof PsiJavaFile && !(file instanceof JspFile) &&
          CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
               PsiUtil.isLanguageLevel5OrHigher(file)) {
        if (handleJavaGT(editor, JavaTokenType.LT, JavaTokenType.GT, INVALID_INSIDE_REFERENCE)) return Result.STOP;
      }
    }

    if (c == ';') {
      if (handleSemicolon(editor, fileType)) return Result.STOP;
    }
    if (originalFileType == StdFileTypes.JAVA && c == '{') {
      int offset = editor.getCaretModel().getOffset();
      if (offset == 0) {
        return Result.CONTINUE;
      }

      HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset - 1);
      while (!iterator.atEnd() && iterator.getTokenType() == TokenType.WHITE_SPACE) {
        iterator.retreat();
      }
      if (iterator.atEnd() || iterator.getTokenType() == JavaTokenType.RBRACKET || iterator.getTokenType() == JavaTokenType.EQ) {
        return Result.CONTINUE;
      }
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      final PsiElement leaf = file.findElementAt(offset);
      if (PsiTreeUtil.getParentOfType(leaf, PsiArrayInitializerExpression.class, false, PsiCodeBlock.class, PsiMember.class) != null) {
        return Result.CONTINUE;
      }
      if (PsiTreeUtil.getParentOfType(leaf, PsiCodeBlock.class, false, PsiMember.class) != null) {
        EditorModificationUtil.insertStringAtCaret(editor, "{", false, true);
        TypedHandler.indentOpenedBrace(project, editor);
        return Result.STOP;
      }
    }

    return Result.CONTINUE;
  }

  @Override
  public Result charTyped(final char c, final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    if (myJavaLTTyped) {
      myJavaLTTyped = false;
      handleAfterJavaLT(editor, JavaTokenType.LT, JavaTokenType.GT, INVALID_INSIDE_REFERENCE);
      return Result.STOP;
    }
    else if (c == ':') {
      if (autoIndentCase(editor, project, file)) {
        return Result.STOP;
      }
    }
    return Result.CONTINUE;
  }

  @Nullable
  private static FileType getOriginalFileType(final PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    return virtualFile != null ? virtualFile.getFileType() : null;
  }

  private static boolean handleSemicolon(Editor editor, FileType fileType) {
    if (fileType != StdFileTypes.JAVA) return false;
    int offset = editor.getCaretModel().getOffset();
    if (offset == editor.getDocument().getTextLength()) return false;

    char charAt = editor.getDocument().getCharsSequence().charAt(offset);
    if (charAt != ';') return false;

    editor.getCaretModel().moveToOffset(offset + 1);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    return true;
  }

  //need custom handler, since brace matcher cannot be used
  public static boolean handleJavaGT(final Editor editor,
                                      final IElementType lt,
                                      final IElementType gt,
                                      final TokenSet invalidInsideReference) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return false;

    int offset = editor.getCaretModel().getOffset();

    if (offset == editor.getDocument().getTextLength()) return false;

    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    if (iterator.getTokenType() != gt) return false;
    while (!iterator.atEnd() && !invalidInsideReference.contains(iterator.getTokenType())) {
      iterator.advance();
    }

    if (!iterator.atEnd() && invalidInsideReference.contains(iterator.getTokenType())) iterator.retreat();

    int balance = 0;
    while (!iterator.atEnd() && balance >= 0) {
      final IElementType tokenType = iterator.getTokenType();
      if (tokenType == lt) {
        balance--;
      }
      else if (tokenType == gt) {
        balance++;
      }
      else if (invalidInsideReference.contains(tokenType)) {
        break;
      }

      iterator.retreat();
    }

    if (balance == 0) {
      editor.getCaretModel().moveToOffset(offset + 1);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      return true;
    }

    return false;
  }

  //need custom handler, since brace matcher cannot be used
  public static void handleAfterJavaLT(final Editor editor,
                                        final IElementType lt,
                                        final IElementType gt,
                                        final TokenSet invalidInsideReference) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return;

    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    while (iterator.getStart() > 0 && !invalidInsideReference.contains(iterator.getTokenType())) {
      iterator.retreat();
    }

    if (invalidInsideReference.contains(iterator.getTokenType())) iterator.advance();

    int balance = 0;
    while (!iterator.atEnd() && balance >= 0) {
      final IElementType tokenType = iterator.getTokenType();
      if (tokenType == lt) {
        balance++;
      }
      else if (tokenType == gt) {
        balance--;
      }
      else if (invalidInsideReference.contains(tokenType)) {
        break;
      }

      iterator.advance();
    }

    if (balance == 1) {
      editor.getDocument().insertString(offset, ">");
    }
  }

  private static void autoPopupJavadocLookup(final Project project, final Editor editor) {
    AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, new Condition<PsiFile>() {
      @Override
      public boolean value(PsiFile file) {
        int offset = editor.getCaretModel().getOffset();

        PsiElement lastElement = file.findElementAt(offset - 1);
        return lastElement != null && StringUtil.endsWithChar(lastElement.getText(), '@');
      }
    });
  }

  public static boolean isAfterClassLikeIdentifierOrDot(final int offset, final Editor editor) {
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()) return false;
    if (iterator.getStart() > 0) iterator.retreat();
    final IElementType tokenType = iterator.getTokenType();
    if (tokenType == JavaTokenType.DOT) return true;
    return isClassLikeIdentifier(offset, editor, iterator, JavaTokenType.IDENTIFIER);
  }

  public static boolean isClassLikeIdentifier(int offset, Editor editor, HighlighterIterator iterator, final IElementType idType) {
    if (iterator.getTokenType() == idType && iterator.getEnd() == offset) {
      final CharSequence chars = editor.getDocument().getCharsSequence();
      final char startChar = chars.charAt(iterator.getStart());
      if (!Character.isUpperCase(startChar)) return false;
      final CharSequence word = chars.subSequence(iterator.getStart(), iterator.getEnd());
      if (word.length() == 1) return true;
      for (int i = 1; i < word.length(); i++) {
        if (Character.isLowerCase(word.charAt(i))) return true;
      }
    }

    return false;
  }
  
  private static boolean autoIndentCase(Editor editor, Project project, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    PsiElement currElement = file.findElementAt(offset - 1);
    if (currElement != null) {
      PsiElement parent = currElement.getParent();
      if (parent != null && parent instanceof PsiSwitchLabelStatement) {
        CodeStyleManager.getInstance(project).adjustLineIndent(file, parent.getTextOffset());
        return true;
      }
    }
    return false;
  }
}
