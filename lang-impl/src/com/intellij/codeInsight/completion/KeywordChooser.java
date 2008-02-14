package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 17.02.2003
 * Time: 16:53:09
 * To change this template use Options | File Templates.
 */
public interface KeywordChooser{
  String[] getKeywords(CompletionContext context, PsiElement position);
}
