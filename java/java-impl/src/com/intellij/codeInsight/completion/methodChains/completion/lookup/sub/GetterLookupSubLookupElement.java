package com.intellij.codeInsight.completion.methodChains.completion.lookup.sub;

import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class GetterLookupSubLookupElement implements SubLookupElement {
  private final String myVariableName;
  private final String myMethodName;

  public GetterLookupSubLookupElement(final String methodName) {
    this(null, methodName);
  }

  public GetterLookupSubLookupElement(@Nullable final String variableName, final String methodName) {
    myVariableName = variableName;
    myMethodName = methodName;
  }

  @Override
  public void doImport(final PsiJavaFile javaFile) {
  }

  @Override
  public String getInsertString() {
    final StringBuilder sb = new StringBuilder();
    if (myVariableName != null) {
      sb.append(myVariableName).append(".");
    }
    sb.append(myMethodName).append("()");
    return sb.toString();
  }
}
