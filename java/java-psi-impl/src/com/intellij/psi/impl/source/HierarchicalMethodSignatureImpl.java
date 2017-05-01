/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
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
  @NotNull
  public List<HierarchicalMethodSignature> getSuperSignatures() {
    return mySupers == null ? Collections.emptyList() : mySupers;
  }

  @NotNull
  @Override
  public List<HierarchicalMethodSignature> getInaccessibleSuperSignatures() {
    return myInaccessibleSupers == null ? super.getInaccessibleSuperSignatures() : myInaccessibleSupers;
  }
}
