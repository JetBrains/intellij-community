// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public class PsiTypeCodeFragmentImpl extends PsiCodeFragmentImpl implements PsiTypeCodeFragment {
  private final static Logger LOG = Logger.getInstance(PsiTypeCodeFragmentImpl.class);

  private final boolean myAllowEllipsis;
  private final boolean myAllowDisjunction;
  private final boolean myAllowConjunction;

  public PsiTypeCodeFragmentImpl(Project project,
                                 boolean isPhysical,
                                 @NonNls String name,
                                 CharSequence text,
                                 int flags,
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
      final PsiErrorElement error;

      MyTypeSyntaxException(PsiErrorElement e) {
        super(e.getErrorDescription());
        error = e;
      }
    }

    try {
      accept(new PsiRecursiveElementWalkingVisitor() {
        @Override
        public void visitErrorElement(@NotNull PsiErrorElement element) {
          throw new MyTypeSyntaxException(element);
        }
      });
    }
    catch (MyTypeSyntaxException e) {
      throw new TypeSyntaxException(e.getMessage(), e.error.getTextRange().getStartOffset());
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
    else if (type instanceof PsiIntersectionType && !myAllowConjunction) {
      throw new TypeSyntaxException("Conjunction not allowed: " + type);
    }
    return type;
  }

  @Override
  public boolean isVoidValid() {
    return getOriginalFile().getUserData(PsiUtil.VALID_VOID_TYPE_IN_CODE_FRAGMENT) != null;
  }
}
