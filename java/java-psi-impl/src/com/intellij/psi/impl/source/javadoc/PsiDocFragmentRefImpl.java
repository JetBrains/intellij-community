// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.javadoc.PsiDocFragmentName;
import com.intellij.psi.javadoc.PsiDocFragmentRef;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiDocFragmentRefImpl extends CompositePsiElement implements PsiDocFragmentRef, Constants {
  public PsiDocFragmentRefImpl() {
    super(DOC_FRAGMENT_REF);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocTagValue(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @Nullable PsiElement getScope() {
    // Find references to other package-info.java files 
    ASTNode holder = getNode().findChildByType(DOC_REFERENCE_HOLDER);
    if (holder != null) {
      PsiElement reference = holder.getPsi().getFirstChild();
      if (reference instanceof PsiJavaCodeReferenceElement) {
        PsiElement target = ((PsiJavaCodeReferenceElement)reference).resolve();
        if (target instanceof PsiPackage) {
          return target;
        }
      }
    }

    PsiClass scope = PsiDocMethodOrFieldRef.getScope(this);
    if (scope != null) return scope;

    // Find references to the package statement in the local package-info.java
    PsiFile file = getContainingFile();
    if (file instanceof PsiJavaFile && PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
      return JavaPsiFacade.getInstance(getProject()).findPackage(((PsiJavaFile)file).getPackageName());
    }
    return null;
  }

  @Override
  public @Nullable PsiDocFragmentName getFragmentName() {
    return PsiTreeUtil.getChildOfType(this, PsiDocFragmentName.class);
  }
}
