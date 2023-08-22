/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class EndOfLineCommentPredicate implements PsiElementPredicate {

  private static final Pattern NO_INSPECTION_PATTERN =
    Pattern.compile("//[\t ]*noinspection .*");

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiComment comment)) {
      return false;
    }
    if (element instanceof PsiDocComment) {
      return false;
    }
    final IElementType type = comment.getTokenType();
    if (!JavaTokenType.END_OF_LINE_COMMENT.equals(type)) {
      return false;
    }
    final String text = comment.getText();
    final Matcher matcher = NO_INSPECTION_PATTERN.matcher(text);
    return !matcher.matches() && !text.startsWith("#!");
  }
}