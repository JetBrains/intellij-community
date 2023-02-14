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

package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.classMembers.AbstractUsesDependencyMemberInfoModel;
import org.jetbrains.annotations.NotNull;

public class UsesDependencyMemberInfoModel<T extends NavigatablePsiElement, C extends PsiElement, M extends MemberInfoBase<T>>
  extends AbstractUsesDependencyMemberInfoModel<T,C,M> {

  public UsesDependencyMemberInfoModel(C aClass, C superClass, boolean recursive) {
    super(aClass, superClass, recursive);
  }

  @Override
  protected int doCheck(@NotNull M memberInfo, int problem) {
    final PsiElement member = memberInfo.getMember();
    if(problem == ERROR
            && member instanceof PsiModifierListOwner
            && ((PsiModifierListOwner) member).hasModifierProperty(PsiModifier.STATIC)) {
      return WARNING;
    }
    return problem;
  }

}
