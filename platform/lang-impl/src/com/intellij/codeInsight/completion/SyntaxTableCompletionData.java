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

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.TrueFilter;

/**
 * @author Maxim.Mossienko
 */
public class SyntaxTableCompletionData extends CompletionData{
  private final SyntaxTable mySyntaxTable;

  public SyntaxTableCompletionData(SyntaxTable _syntaxTable) {
    mySyntaxTable = _syntaxTable;
    mySyntaxTable.getKeywords1();

    final CompletionVariant variant = new CompletionVariant(TrueFilter.INSTANCE);
    variant.includeScopeClass(PsiElement.class, true);
    variant.addCompletionFilter(TrueFilter.INSTANCE);
    final String[] empty = {};

    variant.addCompletion(mySyntaxTable.getKeywords1().toArray(empty), TailType.NONE);
    variant.addCompletion(mySyntaxTable.getKeywords2().toArray(empty), TailType.NONE);
    variant.addCompletion(mySyntaxTable.getKeywords3().toArray(empty), TailType.NONE);
    variant.addCompletion(mySyntaxTable.getKeywords4().toArray(empty), TailType.NONE);

    registerVariant(variant);
  }

  public String findPrefix(PsiElement insertedElement, int offset) {
    String text = insertedElement.getText();
    int offsetInElement = offset - insertedElement.getTextOffset();
    int start = offsetInElement - 1;
    while(start >=0 ) {
      if(!Character.isJavaIdentifierStart(text.charAt(start))) break;
      --start;
    }
    return text.substring(start+1, offsetInElement).trim();
  }
}
