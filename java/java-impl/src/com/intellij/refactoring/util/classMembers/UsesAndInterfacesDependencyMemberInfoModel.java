/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 25.06.2002
 * Time: 14:01:08
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
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
                      public boolean checkedInterfacesContain(PsiMethod psiMethod) {
                        return false;
                      }
                    };

  public UsesAndInterfacesDependencyMemberInfoModel(PsiClass aClass, @Nullable PsiClass superClass, boolean recursive,
                                                    @NotNull final InterfaceContainmentVerifier interfaceContainmentVerifier) {
    super(new ANDCombinedMemberInfoModel<>(
      new UsesDependencyMemberInfoModel<T, PsiClass, M>(aClass, superClass, recursive) {
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
