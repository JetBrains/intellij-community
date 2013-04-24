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
package com.intellij.codeInsight.completion.util;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
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

  protected ParenthesesInsertHandler(final boolean spaceBeforeParentheses,
                                     final boolean spaceBetweenParentheses,
                                     final boolean mayInsertRightParenthesis) {
    this(spaceBeforeParentheses, spaceBetweenParentheses, mayInsertRightParenthesis, false);
  }

  protected ParenthesesInsertHandler(boolean spaceBeforeParentheses,
                                     boolean spaceBetweenParentheses,
                                     boolean mayInsertRightParenthesis,
                                     boolean allowParametersOnNextLine) {
    mySpaceBeforeParentheses = spaceBeforeParentheses;
    mySpaceBetweenParentheses = spaceBetweenParentheses;
    myMayInsertRightParenthesis = mayInsertRightParenthesis;
    myAllowParametersOnNextLine = allowParametersOnNextLine;
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
    PsiElement element = findNextToken(context);

    final char completionChar = context.getCompletionChar();
    final boolean putCaretInside = completionChar == '(' || placeCaretInsideParentheses(context, item);

    if (completionChar == '(') {
      context.setAddCompletionChar(false);
    }

    if (isToken(element, "(")) {
      int lparenthOffset = element.getTextRange().getStartOffset();
      if (mySpaceBeforeParentheses && lparenthOffset == context.getTailOffset()) {
        document.insertString(context.getTailOffset(), " ");
        lparenthOffset++;
      }

      if (completionChar == '(' || completionChar == '\t') {
        editor.getCaretModel().moveToOffset(lparenthOffset + 1);
      } else {
        editor.getCaretModel().moveToOffset(context.getTailOffset());
      }

      context.setTailOffset(lparenthOffset + 1);

      PsiElement list = element.getParent();
      PsiElement last = list.getLastChild();
      if (isToken(last, ")")) {
        int rparenthOffset = last.getTextRange().getStartOffset();
        context.setTailOffset(rparenthOffset + 1);
        if (!putCaretInside) {
          for (int i = lparenthOffset + 1; i < rparenthOffset; i++) {
            if (!Character.isWhitespace(document.getCharsSequence().charAt(i))) {
              return;
            }
          }
          editor.getCaretModel().moveToOffset(context.getTailOffset());
        } else if (mySpaceBetweenParentheses && document.getCharsSequence().charAt(lparenthOffset) == ' ') {
          editor.getCaretModel().moveToOffset(lparenthOffset + 2);
        } else {
          editor.getCaretModel().moveToOffset(lparenthOffset + 1);
        }
        return;
      }
    } else {
      document.insertString(context.getTailOffset(), getSpace(mySpaceBeforeParentheses) + "(" + getSpace(mySpaceBetweenParentheses));
      editor.getCaretModel().moveToOffset(context.getTailOffset());
    }

    if (!myMayInsertRightParenthesis) return;

    if (context.getCompletionChar() == '(') {
      //todo use BraceMatchingUtil.isPairedBracesAllowedBeforeTypeInFileType
      int tail = context.getTailOffset();
      if (tail < document.getTextLength() && StringUtil.isJavaIdentifierPart(document.getCharsSequence().charAt(tail))) {
        return;
      }
    }

    document.insertString(context.getTailOffset(), getSpace(mySpaceBetweenParentheses) + ")");
    if (!putCaretInside) {
      editor.getCaretModel().moveToOffset(context.getTailOffset());
    }
  }

  private static String getSpace(boolean needSpace) {
    return needSpace ? " " : "";
  }

  @Nullable
  protected PsiElement findNextToken(final InsertionContext context) {
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
