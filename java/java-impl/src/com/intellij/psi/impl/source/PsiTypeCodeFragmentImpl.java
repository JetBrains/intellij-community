/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.BitUtil.isSet;

/**
 * @author dsl
 */
public class PsiTypeCodeFragmentImpl extends PsiCodeFragmentImpl implements PsiTypeCodeFragment {
  private final static Logger LOG = Logger.getInstance(PsiTypeCodeFragmentImpl.class);

  private final boolean myAllowEllipsis;
  private final boolean myAllowDisjunction;
  private final boolean myAllowConjunction;

  public PsiTypeCodeFragmentImpl(final Project project,
                                 final boolean isPhysical,
                                 @NonNls final String name,
                                 final CharSequence text,
                                 final int flags,
                                 PsiElement context) {
    super(project,
          isSet(flags, JavaCodeFragmentFactory.ALLOW_INTERSECTION) ? JavaElementType.TYPE_WITH_CONJUNCTIONS_TEXT : JavaElementType.TYPE_WITH_DISJUNCTIONS_TEXT,
          isPhysical,
          name,
          text,
          context);

    myAllowEllipsis = isSet(flags, JavaCodeFragmentFactory.ALLOW_ELLIPSIS);
    myAllowDisjunction = isSet(flags, JavaCodeFragmentFactory.ALLOW_DISJUNCTION);
    myAllowConjunction = isSet(flags, JavaCodeFragmentFactory.ALLOW_INTERSECTION);
    LOG.assertTrue(!myAllowConjunction || !myAllowDisjunction);

    if (isSet(flags, JavaCodeFragmentFactory.ALLOW_VOID)) {
      putUserData(PsiUtil.VALID_VOID_TYPE_IN_CODE_FRAGMENT, Boolean.TRUE);
    }
  }

  @Override
  @NotNull
  public PsiType getType() throws TypeSyntaxException, NoTypeException {
    class MyTypeSyntaxException extends RuntimeException {
      MyTypeSyntaxException(final String message) { super(message); }
    }

    try {
      accept(new PsiRecursiveElementWalkingVisitor() {
        @Override
        public void visitErrorElement(PsiErrorElement element) {
          throw new MyTypeSyntaxException(element.getErrorDescription());
        }
      });
    }
    catch (MyTypeSyntaxException e) {
      throw new TypeSyntaxException(e.getMessage());
    }

    final PsiTypeElement typeElement = PsiTreeUtil.getChildOfType(this, PsiTypeElement.class);
    if (typeElement == null) {
      throw new NoTypeException("No type found in '" + getText() + "'");
    }

    final PsiType type = typeElement.getType();
    if (type instanceof PsiEllipsisType && !myAllowEllipsis) {
      throw new TypeSyntaxException("Ellipsis not allowed: " + type);
    }
    else if (type instanceof PsiDisjunctionType && !myAllowDisjunction) {
      throw new TypeSyntaxException("Disjunction not allowed: " + type);
    }
    else if (type instanceof PsiDisjunctionType && !myAllowConjunction) {
      throw new TypeSyntaxException("Conjunction not allowed: " + type);
    }
    return type;
  }

  @Override
  public boolean isVoidValid() {
    return getOriginalFile().getUserData(PsiUtil.VALID_VOID_TYPE_IN_CODE_FRAGMENT) != null;
  }
}
