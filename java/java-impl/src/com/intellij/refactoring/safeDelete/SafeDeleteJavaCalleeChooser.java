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
package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.MemberNodeBase;
import com.intellij.refactoring.changeSignature.inCallers.AbstractJavaMemberCallerChooser;
import com.intellij.refactoring.changeSignature.inCallers.JavaMemberNode;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteMemberCalleeUsageInfo;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceJavaDeleteUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

abstract class SafeDeleteJavaCalleeChooser extends AbstractJavaMemberCallerChooser<PsiMember> {
  private final Project myProject;

  public SafeDeleteJavaCalleeChooser(PsiMember member,
                                     Project project,
                                     ArrayList<UsageInfo> result) {
    super(member, project, "Select Members To Cascade Safe Delete", null, members -> result.addAll(ContainerUtil.map(members, m -> {
      return new SafeDeleteReferenceJavaDeleteUsageInfo(m, m, true);
    })));
    myProject = project;
  }

  protected abstract ArrayList<SafeDeleteMemberCalleeUsageInfo> getTopLevelItems();

  @NotNull
  @Override
  protected String getMemberTypePresentableText() {
    return "member";
  }

  @Override
  protected PsiMember[] findDeepestSuperMethods(PsiMember method) {
    return method instanceof PsiMethod ? ((PsiMethod)method).findDeepestSuperMethods() : PsiMember.EMPTY_ARRAY;
  }

  @Nullable
  static List<PsiMember> computeCalleesSafeToDelete(final PsiMember psiMember) {
    final PsiElement body;
    if (psiMember instanceof PsiMethod) {
      body = ((PsiMethod)psiMember).getBody();
    } else {
      assert psiMember instanceof PsiField;
      body = ((PsiField)psiMember).getInitializer();
    }
    if (body != null) {
      final PsiClass containingClass = psiMember.getContainingClass();
      if (containingClass != null) {
        final Set<PsiMember> membersToCheck = new HashSet<>();
        body.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            PsiElement resolved = expression.resolve();
            if (resolved instanceof PsiMethod || resolved instanceof PsiField) {
              ContainerUtil.addAllNotNull(membersToCheck, (PsiMember) resolved);
            }
          }
        });

        return membersToCheck
          .stream()
          .filter(m -> containingClass.equals(m.getContainingClass()) && !psiMember.equals(m))
          .filter(m -> !(m instanceof PsiMethod) || ((PsiMethod)m).findDeepestSuperMethods().length == 0)
          .filter(m -> usedOnlyIn(m, psiMember))
          .collect(Collectors.toList());
      }
    }
    return null;
  }

  @Override
  protected JavaMemberNode<PsiMember> createTreeNodeFor(PsiMember nodeMethod,
                                                        com.intellij.util.containers.HashSet<PsiMember> callees,
                                                        Runnable cancelCallback) {
    final SafeDeleteJavaMemberNode node = new SafeDeleteJavaMemberNode(nodeMethod, callees, cancelCallback, nodeMethod != null ? nodeMethod.getProject() : myProject);
    if (getTopMember().equals(nodeMethod)) {
      node.setEnabled(false);
      node.setChecked(true);
    }
    return node;
  }

  @Override
  protected MemberNodeBase<PsiMember> getCalleeNode(MemberNodeBase<PsiMember> node) {
    return node;
  }

  @Override
  protected MemberNodeBase<PsiMember> getCallerNode(MemberNodeBase<PsiMember> node) {
    return (MemberNodeBase<PsiMember>)node.getParent();
  }

  private class SafeDeleteJavaMemberNode extends JavaMemberNode<PsiMember> {

    public SafeDeleteJavaMemberNode(PsiMember currentMember,
                                    HashSet<PsiMember> callees,
                                    Runnable cancelCallback,
                                    Project project) {
      super(currentMember, callees, project, cancelCallback);
    }

    @Override
    protected MemberNodeBase<PsiMember> createNode(PsiMember caller, HashSet<PsiMember> callees) {
      return new SafeDeleteJavaMemberNode(caller, callees, myCancelCallback, myProject);
    }

    @Override
    protected List<PsiMember> computeCallers() {
      if (getTopMember().equals(getMember())) {
        return ContainerUtil.map(getTopLevelItems(), info -> info.getCalledMember());
      }

      final List<PsiMember> callees = computeCalleesSafeToDelete(getMember());
      if (callees != null) {
        callees.remove(getTopMember());
        return callees;
      }
      else {
        return Collections.emptyList();
      }
    }

    @Override
    protected Condition<PsiMember> getFilter() {
      return member -> !getMember().equals(member);
    }
  }

  private static boolean usedOnlyIn(@NotNull PsiMember explored, @NotNull PsiMember place) {
    return ReferencesSearch.search(explored).forEach(
      new CommonProcessors.CollectProcessor<PsiReference>() {
        @Override
        public boolean process(PsiReference reference) {
          final PsiElement element = reference.getElement();
          return PsiTreeUtil.isAncestor(place, element, true) ||
                 PsiTreeUtil.isAncestor(explored, element, true);
        }
      });
  }
}