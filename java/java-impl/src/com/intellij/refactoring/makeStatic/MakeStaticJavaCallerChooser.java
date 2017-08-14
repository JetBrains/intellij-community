/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.makeStatic;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.MemberNodeBase;
import com.intellij.refactoring.changeSignature.inCallers.JavaCallerChooser;
import com.intellij.refactoring.changeSignature.inCallers.JavaMethodNode;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

abstract class MakeStaticJavaCallerChooser extends JavaCallerChooser {
  private final Project myProject;

  public MakeStaticJavaCallerChooser(PsiMethod method, Project project, Consumer<Set<PsiMethod>> consumer) {
    super(method, project, "Select Methods To Propagate Static", null, consumer);
    myProject = project;
  }

  static PsiMethod isTheLastClassRef(PsiElement element, PsiMethod member) {
    final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    if ( containingMethod != null &&
        !containingMethod.hasModifierProperty(PsiModifier.STATIC) &&
        !containingMethod.isConstructor() &&
         containingMethod.findDeepestSuperMethods().length == 0 &&
        !containingMethod.equals(member) &&
         OverridingMethodsSearch.search(containingMethod).findFirst() == null) {
      final PsiClass containingClass = containingMethod.getContainingClass();
      if (containingClass != null) {
        if (ClassUtil.isTopLevelClass(containingClass) || containingClass.hasModifierProperty(PsiModifier.STATIC)) {
          final InternalUsageInfo[] refsInMember = MakeStaticUtil.findClassRefsInMember(containingMethod, true);
          for (InternalUsageInfo info : refsInMember) {
            final PsiElement referencedElement = info.getReferencedElement();
            if (!member.equals(referencedElement) && !containingMethod.equals(referencedElement)) {
              return null;
            }
          }
          return containingMethod;
        }
      }
    }
    return null;
  }

  protected abstract ArrayList<UsageInfo> getTopLevelItems();

  @Override
  protected JavaMethodNode createTreeNode(PsiMethod nodeMethod,
                                          com.intellij.util.containers.HashSet<PsiMethod> called,
                                          Runnable cancelCallback) {
    final MakeStaticJavaMethodNode node =
      new MakeStaticJavaMethodNode(nodeMethod, called, cancelCallback, nodeMethod != null ? nodeMethod.getProject() : myProject);
    if (getTopMember().equals(nodeMethod)) {
      node.setEnabled(false);
      node.setChecked(true);
    }
    return node;
  }

  private class MakeStaticJavaMethodNode extends JavaMethodNode {
    public MakeStaticJavaMethodNode(PsiMethod currentMethod,
                                    HashSet<PsiMethod> called,
                                    Runnable cancelCallback,
                                    Project project) {
      super(currentMethod, called, project, cancelCallback);
    }

    @Override
    protected List<PsiMethod> computeCallers() {
      if (getTopMember().equals(getMember())) {
        final ArrayList<UsageInfo> items = getTopLevelItems();
        return ContainerUtil.map(items, info -> (PsiMethod)info.getElement());
      }
      return super.computeCallers();
    }


    @Override
    protected MemberNodeBase<PsiMethod> createNode(PsiMethod caller, HashSet<PsiMethod> called) {
      return new MakeStaticJavaMethodNode(caller, called, myCancelCallback, myProject);
    }

    @Override
    protected Condition<PsiMethod> getFilter() {
      return method -> !myMethod.equals(method) && isTheLastClassRef(method, myMethod) != null;
    }
  }
}
