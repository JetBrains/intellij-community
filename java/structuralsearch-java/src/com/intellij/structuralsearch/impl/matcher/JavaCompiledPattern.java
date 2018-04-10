/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.strategies.JavaMatchingStrategy;
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
    setStrategy(JavaMatchingStrategy.getInstance());
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
