package com.intellij.codeInsight.completion.methodChains.completion.lookup.sub;

import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiVariable;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class VariableSubLookupElement implements SubLookupElement {

  private final String myVarName;

  public VariableSubLookupElement(final PsiVariable variable) {
    myVarName = variable.getName();
  }

  @Override
  public void doImport(final PsiJavaFile javaFile) {
  }

  @Override
  public String getInsertString() {
    return myVarName;
  }
}
