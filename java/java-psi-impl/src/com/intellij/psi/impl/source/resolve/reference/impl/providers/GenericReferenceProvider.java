/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 17:23:43
 * To change this template use Options | File Templates.
 */
public abstract class GenericReferenceProvider extends PsiReferenceProvider {
  private boolean mySoft = false;

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
    PsiScopesUtil.treeWalkUp(processor, position, null);
  }

  public void setSoft(boolean softFlag) {
    mySoft = softFlag;
  }

  public boolean isSoft() {
    return mySoft;
  }
}
