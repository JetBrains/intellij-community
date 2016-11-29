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
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;

/**
* @author Eugene.Kudelevsky
*/
public class JavaLexicalNodesFilter extends JavaElementVisitor {
  private final LexicalNodesFilter myLexicalNodesFilter;

  public JavaLexicalNodesFilter(LexicalNodesFilter lexicalNodesFilter) {
    this.myLexicalNodesFilter = lexicalNodesFilter;
  }

  @Override public void visitJavaToken(final PsiJavaToken t) {
    myLexicalNodesFilter.setResult(true);
  }

  @Override public void visitComment(final PsiComment comment) {
  }

  @Override public void visitDocComment(final PsiDocComment comment) {
  }

  @Override public void visitKeyword(PsiKeyword keyword) {
    myLexicalNodesFilter.setResult(!myLexicalNodesFilter.isCareKeyWords());
  }

  @Override public void visitWhiteSpace(final PsiWhiteSpace space) {
    myLexicalNodesFilter.setResult(true);
  }

  @Override public void visitErrorElement(final PsiErrorElement element) {
  }
}
