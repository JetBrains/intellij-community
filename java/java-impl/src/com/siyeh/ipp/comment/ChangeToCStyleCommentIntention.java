/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.comment;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class ChangeToCStyleCommentIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("change.to.c.style.comment.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("change.to.c.style.comment.intention.name");
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new EndOfLineCommentPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    PsiComment firstComment = (PsiComment)element;
    while (true) {
      final PsiElement prevComment = PsiTreeUtil.skipWhitespacesBackward(firstComment);
      if (!(prevComment instanceof PsiComment) || ((PsiComment)prevComment).getTokenType() != JavaTokenType.END_OF_LINE_COMMENT) {
        break;
      }
      firstComment = (PsiComment)prevComment;
    }
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(element.getProject());
    final PsiElementFactory factory = psiFacade.getElementFactory();
    final List<PsiComment> multiLineComments = new ArrayList<>();
    PsiElement nextComment = firstComment;
    String whiteSpace = null;
    while (true) {
      nextComment = PsiTreeUtil.skipWhitespacesForward(nextComment);
      if (!(nextComment instanceof PsiComment) || ((PsiComment)nextComment).getTokenType() != JavaTokenType.END_OF_LINE_COMMENT) {
        break;
      }
      if (whiteSpace == null) {
        final PsiElement prevSibling = nextComment.getPrevSibling();
        assert prevSibling != null;
        final String text = prevSibling.getText();
        whiteSpace = getIndent(text);
      }
      multiLineComments.add((PsiComment)nextComment);
    }
    final String newCommentString;
    if (multiLineComments.isEmpty()) {
      final String text = getCommentContents(firstComment);
      newCommentString = "/* " + text.trim() + " */";
    }
    else {
      final StringBuilder text = new StringBuilder();
      text.append("/*\n");
      text.append(whiteSpace);
      text.append(getCommentContents(firstComment));
      for (PsiComment multiLineComment : multiLineComments) {
        text.append('\n');
        text.append(whiteSpace);
        text.append(getCommentContents(multiLineComment));
      }
      text.append('\n');
      text.append(whiteSpace);
      text.append("*/");
      newCommentString = text.toString();
    }
    final PsiComment newComment =
      factory.createCommentFromText(newCommentString, element);
    firstComment.replace(newComment);
    for (PsiElement commentToDelete : multiLineComments) {
      commentToDelete.delete();
    }
  }

  private static String getIndent(String whitespace) {
    for (int i = whitespace.length() - 1; i >= 0; i--) {
      final char c = whitespace.charAt(i);
      if (c == '\n') {
        if (i == whitespace.length() - 1) {
          return "";
        }
        return whitespace.substring(i + 1);
      }
    }
    return whitespace;
  }

  private static String getCommentContents(@NotNull PsiComment comment) {
    final String text = comment.getText();
    return StringUtil.replace(text.substring(2), "*/", "* /");
  }
}