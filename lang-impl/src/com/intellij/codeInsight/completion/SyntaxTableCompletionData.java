package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.TrueFilter;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Oct 14, 2004
 * Time: 4:28:39 PM
 * To change this template use File | Settings | File Templates.
 */
public class SyntaxTableCompletionData extends CompletionData{
  private SyntaxTable mySyntaxTable;

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
