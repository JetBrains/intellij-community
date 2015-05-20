package com.intellij.structuralsearch.impl.matcher;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.strategies.ExprMatchingStrategy;
import org.jetbrains.annotations.Nullable;

/**
* @author Eugene.Kudelevsky
*/
public class JavaCompiledPattern extends CompiledPattern {
  private static final String TYPED_VAR_PREFIX = "__$_";

  private boolean requestsSuperFields;
  private boolean requestsSuperMethods;
  private boolean requestsSuperInners;

  public JavaCompiledPattern() {
    setStrategy(ExprMatchingStrategy.getInstance());
  }

  public String[] getTypedVarPrefixes() {
    return new String[] {TYPED_VAR_PREFIX};
  }

  public boolean isTypedVar(final String str) {
    if (str.isEmpty()) return false;
    if (str.charAt(0)=='@') {
      return str.regionMatches(1,TYPED_VAR_PREFIX,0,TYPED_VAR_PREFIX.length());
    } else {
      return str.startsWith(TYPED_VAR_PREFIX);
    }
  }

  @Override
  public boolean isToResetHandler(PsiElement element) {
    return !(element instanceof PsiJavaToken) &&
           !(element instanceof PsiJavaCodeReferenceElement && element.getParent() instanceof PsiAnnotation);
  }

  @Nullable
  @Override
  public String getAlternativeTextToMatch(PsiElement node, String previousText) {
    // Short class name is matched with fully qualified name
    if(node instanceof PsiJavaCodeReferenceElement || node instanceof PsiClass) {
      PsiElement element = (node instanceof PsiJavaCodeReferenceElement)?
                           ((PsiJavaCodeReferenceElement)node).resolve():
                           node;

      if (element instanceof PsiClass) {
        String text = ((PsiClass)element).getQualifiedName();
        if (text != null && text.equals(previousText)) {
          text = ((PsiClass)element).getName();
        }

        if (text != null) {
          return text;
        }
      }
    } else if (node instanceof PsiLiteralExpression) {
      return node.getText();
    }
    return null;
  }

  public static final Key<String> ALL_CLASS_CONTENT_VAR_NAME_KEY = Key.create("AllClassContent");

  public boolean isRequestsSuperFields() {
    return requestsSuperFields;
  }

  public void setRequestsSuperFields(boolean requestsSuperFields) {
    this.requestsSuperFields = requestsSuperFields;
  }

  public boolean isRequestsSuperInners() {
    return requestsSuperInners;
  }

  public void setRequestsSuperInners(boolean requestsSuperInners) {
    this.requestsSuperInners = requestsSuperInners;
  }

  public boolean isRequestsSuperMethods() {
    return requestsSuperMethods;
  }

  public void setRequestsSuperMethods(boolean requestsSuperMethods) {
    this.requestsSuperMethods = requestsSuperMethods;
  }
}
