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

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.CallerChooserBase;
import com.intellij.refactoring.changeSignature.MemberNodeBase;
import com.intellij.refactoring.changeSignature.inCallers.JavaMemberNode;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteMemberCalleeUsageInfo;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceJavaDeleteUsageInfo;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

abstract class SafeDeleteJavaCalleeChooser extends CallerChooserBase<PsiElement> {
  private final Project myProject;

  SafeDeleteJavaCalleeChooser(PsiMember member,
                                     Project project,
                                     ArrayList<UsageInfo> result) {
    super(member, project, JavaRefactoringBundle.message("safe.delete.select.members.to.propagate.dialog.title"), null, "dummy." + JavaFileType.INSTANCE.getDefaultExtension(), members -> result.addAll(ContainerUtil.map(members, m -> {
      return new SafeDeleteReferenceJavaDeleteUsageInfo(m, m, true);
    })));
    myProject = project;
  }

  protected abstract ArrayList<SafeDeleteMemberCalleeUsageInfo> getTopLevelItems();

  @Override
  protected String getEmptyCallerText() {
    return "Caller text with highlighted callee would be shown here";
  }

  @Override
  protected String getEmptyCalleeText() {
    return "Callee text would be shown here";
  }
  
  @Override
  protected PsiElement[] findDeepestSuperMethods(PsiElement method) {
    return method instanceof PsiMethod ? ((PsiMethod)method).findDeepestSuperMethods() : PsiMember.EMPTY_ARRAY;
  }

  @Nullable
  static List<PsiElement> computeCalleesSafeToDelete(final PsiMember psiMember) {
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
        final Set<PsiElement> membersToCheck = new HashSet<>();
        body.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            PsiElement resolved = expression.resolve();
            if (resolved instanceof PsiMethod || resolved instanceof PsiField) {
              ContainerUtil.addAllNotNull(membersToCheck, resolved);
            }
          }

          @Override
          public void visitLiteralExpression(PsiLiteralExpression expression) {
            super.visitLiteralExpression(expression);
            PsiReference @NotNull [] references = expression.getReferences();
            for (PsiReference reference : references) {
              if (reference instanceof PsiPolyVariantReference) {
                PsiElement[] nonMembers = Arrays.stream(((PsiPolyVariantReference)reference).multiResolve(false))
                  .map(result -> result.getElement())
                  .filter(e -> !(e instanceof PsiMember))
                  .toArray(PsiElement[]::new);
                ContainerUtil.addAllNotNull(membersToCheck, nonMembers);
              }
              else {
                PsiElement resolve = reference.resolve();
                if (resolve != null && !(resolve instanceof PsiMember)) {
                  membersToCheck.add(resolve);
                }
              }
            }
          }
        });

        return membersToCheck
          .stream()
          .filter(m -> !(m instanceof PsiMember) || containingClass.equals(((PsiMember)m).getContainingClass()) && !psiMember.equals(m))
          .filter(m -> !(m instanceof PsiMethod) || ((PsiMethod)m).findDeepestSuperMethods().length == 0)
          .filter(m -> usedOnlyIn(m, psiMember))
          .collect(Collectors.toList());
      }
    }
    return null;
  }

  @Override
  protected MemberNodeBase<PsiElement> createTreeNodeFor(PsiElement nodeMethod,
                                                        HashSet<PsiElement> callees,
                                                        Runnable cancelCallback) {
    final SafeDeleteJavaMemberNode node = new SafeDeleteJavaMemberNode(nodeMethod, callees, cancelCallback, nodeMethod != null ? nodeMethod.getProject() : myProject);
    if (getTopMember().equals(nodeMethod)) {
      node.setEnabled(false);
      node.setChecked(true);
    }
    return node;
  }

  @Override
  protected MemberNodeBase<PsiElement> getCalleeNode(MemberNodeBase<PsiElement> node) {
    return node;
  }

  @Override
  protected MemberNodeBase<PsiElement> getCallerNode(MemberNodeBase<PsiElement> node) {
    return (MemberNodeBase<PsiElement>)node.getParent();
  }

  private class SafeDeleteJavaMemberNode extends MemberNodeBase<PsiElement> {

    SafeDeleteJavaMemberNode(PsiElement currentMember,
                             HashSet<PsiElement> callees,
                             Runnable cancelCallback,
                             Project project) {
      super(currentMember, callees, project, cancelCallback);
    }

    @Override
    protected void customizeRendererText(ColoredTreeCellRenderer renderer) {
      PsiElement member = getMember();
      if (member instanceof PsiMember) {
        JavaMemberNode.customizeRendererText(renderer, ((PsiMember)member), isEnabled());
      }
      else {
        renderer.append(member.getText()); //todo
      }
    }

    @Override
    protected MemberNodeBase<PsiElement> createNode(PsiElement caller, HashSet<PsiElement> callees) {
      return new SafeDeleteJavaMemberNode(caller, callees, myCancelCallback, myProject);
    }

    @Override
    protected List<PsiElement> computeCallers() {
      PsiElement member = getMember();
      if (getTopMember().equals(member)) {
        return ContainerUtil.map(getTopLevelItems(), info -> info.getCalledElement());
      }

      if (!(member instanceof PsiMember)) return Collections.emptyList();
      final List<PsiElement> callees = computeCalleesSafeToDelete((PsiMember)member);
      if (callees != null) {
        callees.remove(getTopMember());
        return callees;
      }
      else {
        return Collections.emptyList();
      }
    }

    @Override
    protected Condition<PsiElement> getFilter() {
      return member -> !getMember().equals(member);
    }
  }

  private static boolean usedOnlyIn(@NotNull PsiElement explored, @NotNull PsiMember place) {
    CommonProcessors.FindProcessor<PsiReference> findProcessor = new CommonProcessors.FindProcessor<PsiReference>() {
      @Override
      protected boolean accept(PsiReference reference) {
        final PsiElement element = reference.getElement();
        return !PsiTreeUtil.isAncestor(place, element, true) &&
               !PsiTreeUtil.isAncestor(explored, element, true);
      }
    };
    return ReferencesSearch.search(explored).forEach(findProcessor);
  }
}