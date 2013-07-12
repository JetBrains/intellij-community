package com.intellij.codeInsight.completion.methodChains.completion.lookup.sub;

import com.intellij.psi.PsiJavaFile;

/**
 * @author Dmitry Batkovich
 */
public interface SubLookupElement {

  void doImport(final PsiJavaFile javaFile);

  String getInsertString();
}
