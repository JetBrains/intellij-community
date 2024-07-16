// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class HierarchicalMethodSignatureImpl extends HierarchicalMethodSignature {
  private List<HierarchicalMethodSignature> mySupers;
  private List<HierarchicalMethodSignature> myInaccessibleSupers;

  public HierarchicalMethodSignatureImpl(@NotNull MethodSignatureBackedByPsiMethod signature) {
    super(signature);
  }

  public void addSuperSignature(@NotNull HierarchicalMethodSignature superSignatureHierarchical) {
    PsiMethod superMethod = superSignatureHierarchical.getMethod();
    PsiMethod method = getMethod();
    if (PsiUtil.isAccessible(method.getProject(), superMethod, method, null)) {
      if (mySupers == null) mySupers = new SmartList<>();
      mySupers.add(superSignatureHierarchical);
    }
    else {
      if (myInaccessibleSupers == null) myInaccessibleSupers = new SmartList<>();
      myInaccessibleSupers.add(superSignatureHierarchical);
    }
  }

  @Override
  public @NotNull List<HierarchicalMethodSignature> getSuperSignatures() {
    return mySupers == null ? Collections.emptyList() : mySupers;
  }

  @Override
  public @NotNull List<HierarchicalMethodSignature> getInaccessibleSuperSignatures() {
    return myInaccessibleSupers == null ? super.getInaccessibleSuperSignatures() : myInaccessibleSupers;
  }
}
