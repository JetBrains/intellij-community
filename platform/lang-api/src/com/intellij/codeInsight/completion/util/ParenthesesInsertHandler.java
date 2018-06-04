// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.util;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.editorActions.TabOutScopesTracker;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class ParenthesesInsertHandler<T extends LookupElement> implements InsertHandler<T> {
  public static final ParenthesesInsertHandler<LookupElement> WITH_PARAMETERS = new ParenthesesInsertHandler<LookupElement>() {
    @Override
    protected boolean placeCaretInsideParentheses(final InsertionContext context, final LookupElement item) {
      return true;
    }
  };
  public static final ParenthesesInsertHandler<LookupElement> NO_PARAMETERS = new ParenthesesInsertHandler<LookupElement>() {
    @Override
    protected boolean placeCaretInsideParentheses(final InsertionContext context, final LookupElement item) {
      return false;
    }
  };

  public static ParenthesesInsertHandler<LookupElement> getInstance(boolean hasParameters) {
    return hasParameters ? WITH_PARAMETERS : NO_PARAMETERS;
  }

  public static ParenthesesInsertHandler<LookupElement> getInstance(final boolean hasParameters, final boolean spaceBeforeParentheses,
                                                                    final boolean spaceBetweenParentheses,
                                                                    final boolean insertRightParenthesis, boolean allowParametersOnNextLine) {
    return new ParenthesesInsertHandler<LookupElement>(spaceBeforeParentheses, spaceBetweenParentheses, insertRightParenthesis, allowParametersOnNextLine) {
      @Override
      protected boolean placeCaretInsideParentheses(InsertionContext context, LookupElement item) {
        return hasParameters;
      }
    };
  }

  private final boolean mySpaceBeforeParentheses;
  private final boolean mySpaceBetweenParentheses;
  private final boolean myMayInsertRightParenthesis;
  private final boolean myAllowParametersOnNextLine;
  private final char myLeftParenthesis;
  private final char myRightParenthesis;

  protected ParenthesesInsertHandler(final boolean spaceBeforeParentheses,
                                     final boolean spaceBetweenParentheses,
                                     final boolean mayInsertRightParenthesis) {
    this(spaceBeforeParentheses, spaceBetweenParentheses, mayInsertRightParenthesis, false);
  }

  protected ParenthesesInsertHandler(boolean spaceBeforeParentheses,
                                     boolean spaceBetweenParentheses,
                                     boolean mayInsertRightParenthesis,
                                     boolean allowParametersOnNextLine) {
    this(spaceBeforeParentheses, spaceBetweenParentheses, mayInsertRightParenthesis, allowParametersOnNextLine, '(', ')');
  }

  protected ParenthesesInsertHandler(boolean spaceBeforeParentheses, 
                                     boolean spaceBetweenParentheses, 
                                     boolean mayInsertRightParenthesis,
                                     boolean allowParametersOnNextLine, 
                                     char leftParenthesis, 
                                     char rightParenthesis) {
    mySpaceBeforeParentheses = spaceBeforeParentheses;
    mySpaceBetweenParentheses = spaceBetweenParentheses;
    myMayInsertRightParenthesis = mayInsertRightParenthesis;
    myAllowParametersOnNextLine = allowParametersOnNextLine;
    myLeftParenthesis = leftParenthesis;
    myRightParenthesis = rightParenthesis;
  }

  protected ParenthesesInsertHandler() {
    this(false, false, true);
  }

  private static boolean isToken(@Nullable final PsiElement element, final String text) {
    return element != null && text.equals(element.getText());
  }

  protected abstract boolean placeCaretInsideParentheses(final InsertionContext context, final T item);

  @Override
  public void handleInsert(final InsertionContext context, final T item) {
    final Editor editor = context.getEditor();
    final Document document = editor.getDocument();
    context.commitDocument();
    PsiElement lParen = findExistingLeftParenthesis(context);

    final char completionChar = context.getCompletionChar();
    final boolean putCaretInside = completionChar == myLeftParenthesis || placeCaretInsideParentheses(context, item);

    if (completionChar == myLeftParenthesis) {
      context.setAddCompletionChar(false);
    }

    if (lParen != null) {
      int lparenthOffset = lParen.getTextRange().getStartOffset();
      if (mySpaceBeforeParentheses && lparenthOffset == context.getTailOffset()) {
        document.insertString(context.getTailOffset(), " ");
        lparenthOffset++;
      }

      if (completionChar == myLeftParenthesis || completionChar == '\t') {
        editor.getCaretModel().moveToOffset(lparenthOffset + 1);
      } else {
        editor.getCaretModel().moveToOffset(context.getTailOffset());
      }

      context.setTailOffset(lparenthOffset + 1);

      PsiElement list = lParen.getParent();
      PsiElement last = list.getLastChild();
      if (isToken(last, String.valueOf(myRightParenthesis))) {
        int rparenthOffset = last.getTextRange().getStartOffset();
        context.setTailOffset(rparenthOffset + 1);
        if (!putCaretInside) {
          for (int i = lparenthOffset + 1; i < rparenthOffset; i++) {
            if (!Character.isWhitespace(document.getCharsSequence().charAt(i))) {
              return;
            }
          }
          editor.getCaretModel().moveToOffset(context.getTailOffset());
        }
        else if (mySpaceBetweenParentheses && document.getCharsSequence().charAt(lparenthOffset) == ' ') {
          editor.getCaretModel().moveToOffset(lparenthOffset + 2);
        }
        else {
          editor.getCaretModel().moveToOffset(lparenthOffset + 1);
        }
        return;
      }
    } else {
      document.insertString(context.getTailOffset(), getSpace(mySpaceBeforeParentheses) + myLeftParenthesis + getSpace(mySpaceBetweenParentheses));
      editor.getCaretModel().moveToOffset(context.getTailOffset());
    }

    if (!myMayInsertRightParenthesis) return;

    if (context.getCompletionChar() == myLeftParenthesis) {
      //todo use BraceMatchingUtil.isPairedBracesAllowedBeforeTypeInFileType
      int tail = context.getTailOffset();
      if (tail < document.getTextLength() && StringUtil.isJavaIdentifierPart(document.getCharsSequence().charAt(tail))) {
        return;
      }
    }

    document.insertString(context.getTailOffset(), getSpace(mySpaceBetweenParentheses) + myRightParenthesis);
    if (!putCaretInside) {
      editor.getCaretModel().moveToOffset(context.getTailOffset());
    }
    else if (!mySpaceBeforeParentheses) {
      TabOutScopesTracker.getInstance().registerEmptyScopeAtCaret(editor);
    }
  }

  private static String getSpace(boolean needSpace) {
    return needSpace ? " " : "";
  }

  @Nullable
  protected PsiElement findExistingLeftParenthesis(@NotNull InsertionContext context) {
    PsiElement element = findNextToken(context);
    return isToken(element, String.valueOf(myLeftParenthesis)) ? element : null;
  }

  @Nullable
  protected PsiElement findNextToken(@NotNull InsertionContext context) {
    final PsiFile file = context.getFile();
    PsiElement element = file.findElementAt(context.getTailOffset());
    if (element instanceof PsiWhiteSpace) {
      if (!myAllowParametersOnNextLine && element.getText().contains("\n")) {
        return null;
      }
      element = file.findElementAt(element.getTextRange().getEndOffset());
    }
    return element;
  }

}
