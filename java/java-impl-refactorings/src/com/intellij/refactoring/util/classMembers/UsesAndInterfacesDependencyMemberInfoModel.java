// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.classMembers.ANDCombinedMemberInfoModel;
import com.intellij.refactoring.classMembers.DelegatingMemberInfoModel;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UsesAndInterfacesDependencyMemberInfoModel<T extends PsiMember, M extends MemberInfoBase<T>> extends DelegatingMemberInfoModel<T, M> {
  public static final InterfaceContainmentVerifier DEFAULT_CONTAINMENT_VERIFIER = new InterfaceContainmentVerifier() {
                      @Override
                      public boolean checkedInterfacesContain(PsiMethod psiMethod) {
                        return false;
                      }
                    };

  public UsesAndInterfacesDependencyMemberInfoModel(PsiClass aClass, @Nullable PsiClass superClass, boolean recursive,
                                                    final @NotNull InterfaceContainmentVerifier interfaceContainmentVerifier) {
    super(new ANDCombinedMemberInfoModel<>(
      new UsesDependencyMemberInfoModel<>(aClass, superClass, recursive) {
        @Override
        public int checkForProblems(@NotNull M memberInfo) {
          final int problem = super.checkForProblems(memberInfo);
          if (problem == OK) return OK;
          final PsiMember member = memberInfo.getMember();
          if (member instanceof PsiMethod) {
            if (interfaceContainmentVerifier.checkedInterfacesContain((PsiMethod)member)) return OK;
          }
          return problem;
        }
      },
      new InterfaceDependencyMemberInfoModel<>(aClass))
    );
  }


  public void setSuperClass(PsiClass superClass) {
    ((UsesDependencyMemberInfoModel) ((ANDCombinedMemberInfoModel) getDelegatingTarget()).getModel1()).setSuperClass(superClass);
  }


}
