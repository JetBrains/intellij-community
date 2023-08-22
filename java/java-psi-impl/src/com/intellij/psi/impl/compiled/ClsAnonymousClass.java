// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClsAnonymousClass extends ClsClassImpl implements PsiAnonymousClass {
  public ClsAnonymousClass(@NotNull PsiClassStub<?> stub) {
    super(stub);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnonymousClass(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @NotNull PsiJavaCodeReferenceElement getBaseClassReference() {
    return CachedValuesManager.getCachedValue(this, () -> {
      PsiJavaCodeReferenceElement[] refs = getExtendsList().getReferenceElements();
      if (refs.length == 0) {
        refs = getImplementsList().getReferenceElements();
      }
      return CachedValueProvider.Result.create(
        refs.length == 0 ? new ClsJavaCodeReferenceElementImpl(this, CommonClassNames.JAVA_LANG_OBJECT) : refs[0],
        this);
    });
  }

  @Override
  public @NotNull PsiClassType getBaseClassType() {
    return CachedValuesManager.getCachedValue(this, () -> {
      PsiClassType[] refs = getExtendsList().getReferencedTypes();
      if (refs.length == 0) {
        refs = getImplementsList().getReferencedTypes();
      }
      return CachedValueProvider.Result.create(
        refs.length == 0 ? PsiType.getJavaLangObject(getManager(), getResolveScope()) : refs[0],
        this);
    });
  }

  @Override
  public @Nullable PsiExpressionList getArgumentList() {
    return null;
  }

  @Override
  public @Nullable PsiModifierList getModifierList() {
    return null;
  }

  @Override
  public boolean isInQualifiedNew() {
    return false;
  }
}
