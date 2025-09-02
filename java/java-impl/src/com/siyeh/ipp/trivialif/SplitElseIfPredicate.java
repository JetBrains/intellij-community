/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ipp.trivialif;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiStatement;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NonNls;

class SplitElseIfPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken token)) {
      return false;
    }
    final @NonNls String text = element.getText();
    if (!JavaKeywords.ELSE.equals(text)) {
      return false;
    }
    final PsiElement parent = token.getParent();
    if (!(parent instanceof PsiIfStatement ifStatement)) {
      return false;
    }
    final PsiStatement thenBranch = ifStatement.getThenBranch();
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    if (thenBranch == null) {
      return false;
    }
    if (elseBranch == null) {
      return false;
    }
    if (!(elseBranch instanceof PsiIfStatement)) {
      return false;
    }
    return !ErrorUtil.containsError(ifStatement);
  }
}