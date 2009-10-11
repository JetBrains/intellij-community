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
package com.intellij.psi.impl.source;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class PsiTypeCodeFragmentImpl extends PsiCodeFragmentImpl implements PsiTypeCodeFragment {
  private final boolean myAllowEllipsis;

  public PsiTypeCodeFragmentImpl(Project manager,
                                 boolean isPhysical,
                                 boolean allowEllipsis,
                                 @NonNls String name,
                                 CharSequence text) {
    super(manager, JavaElementType.TYPE_TEXT, isPhysical, name, text);
    myAllowEllipsis = allowEllipsis;
  }

  @NotNull
  public PsiType getType()
    throws TypeSyntaxException, NoTypeException {
    class SyntaxError extends RuntimeException {}
    try {
      accept(new PsiRecursiveElementWalkingVisitor() {
        @Override public void visitErrorElement(PsiErrorElement element) {
          throw new SyntaxError();
        }
      });
    }
    catch(SyntaxError e) {
      throw new TypeSyntaxException();
    }
    PsiElement child = getFirstChild();
    while (child != null && !(child instanceof PsiTypeElement)) {
      child = child.getNextSibling();
    }
    PsiTypeElement typeElement = (PsiTypeElement)child;
    if (typeElement == null) {
      throw new NoTypeException();
    }
    PsiType type = typeElement.getType();
    PsiElement sibling = typeElement.getNextSibling();
    while (sibling instanceof PsiWhiteSpace) {
      sibling = sibling.getNextSibling();
    }
    if (sibling instanceof PsiJavaToken && "...".equals(sibling.getText())) {
      if (myAllowEllipsis) return new PsiEllipsisType(type);
      else throw new TypeSyntaxException();
    } else {
      return type;
    }
  }

  public boolean isVoidValid() {
    return getOriginalFile().getUserData(PsiUtil.VALID_VOID_TYPE_IN_CODE_FRAGMENT) != null;
  }
}
