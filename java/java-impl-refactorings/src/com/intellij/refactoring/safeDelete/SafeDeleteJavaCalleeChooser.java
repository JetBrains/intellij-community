// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.safeDelete;

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.CallerChooserBase;
import com.intellij.refactoring.changeSignature.MemberNodeBase;
import com.intellij.refactoring.changeSignature.inCallers.JavaMemberNode;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteMemberCalleeUsageInfo;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceJavaDeleteUsageInfo;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewShortNameLocation;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

abstract class SafeDeleteJavaCalleeChooser extends CallerChooserBase<PsiElement> {
  SafeDeleteJavaCalleeChooser(PsiMember member,
                                     Project project,
                                     ArrayList<UsageInfo> result) {
    super(member, project, JavaRefactoringBundle.message("safe.delete.select.members.to.propagate.dialog.title"), null, "dummy." + JavaFileType.INSTANCE.getDefaultExtension(), members -> result.addAll(ContainerUtil.map(members, m -> {
      return new SafeDeleteReferenceJavaDeleteUsageInfo(m, m, true);
    })));
  }

  protected abstract ArrayList<SafeDeleteMemberCalleeUsageInfo> getTopLevelItems();

  @Override
  protected @NlsContexts.Label String getEmptyCallerText() {
    return JavaRefactoringBundle.message("java.safe.delete.caller.text");
  }

  @Override
  protected @NlsContexts.Label String getEmptyCalleeText() {
    return JavaRefactoringBundle.message("java.safe.delete.empty.callee.text");
  }
  
  @Override
  protected PsiElement[] findDeepestSuperMethods(PsiElement method) {
    return method instanceof PsiMethod ? ((PsiMethod)method).findDeepestSuperMethods() : PsiMember.EMPTY_ARRAY;
  }

  @Nullable
  static List<PsiElement> computeReferencedCodeSafeToDelete(final PsiMember psiMember) {
    final PsiElement body;
    if (psiMember instanceof PsiMethod) {
      body = ((PsiMethod)psiMember).getBody();
    }
    else if (psiMember instanceof PsiField) {
      body = ((PsiField)psiMember).getInitializer();
    }
    else if (psiMember instanceof PsiClass) {
      body = psiMember;
    }
    else {
      body = null;
    }
    if (body != null) {
      final PsiClass containingClass = psiMember.getContainingClass();
      final Set<PsiElement> elementsToCheck = new HashSet<>();
      body.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          super.visitReferenceExpression(expression);
          PsiElement resolved = expression.resolve();
          if (resolved instanceof PsiMethod || resolved instanceof PsiField) {
            ContainerUtil.addAllNotNull(elementsToCheck, resolved);
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
              if (nonMembers.length < 10) {
                ContainerUtil.addAllNotNull(elementsToCheck, nonMembers);
              }
            }
            else {
              PsiElement resolve = reference.resolve();
              if (resolve != null && !(resolve instanceof PsiMember)) {
                elementsToCheck.add(resolve);
              }
            }
          }
        }
      });

      return elementsToCheck
        .stream()
        .filter(m -> !(m instanceof PsiMember) || containingClass != null && containingClass.equals(((PsiMember)m).getContainingClass()) && !psiMember.equals(m))
        .filter(m -> !(m instanceof PsiMethod) || ((PsiMethod)m).findDeepestSuperMethods().length == 0)
        .filter(m -> m.isPhysical())
        .filter(m -> usedOnlyIn(m, psiMember))
        .collect(Collectors.toList());
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

  @Override
  protected @NotNull @Nls String getCalleeEditorTitle() {
    return JavaBundle.message("caller.chooser.referenced.code.title");
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
        renderer.append(ElementDescriptionUtil.getElementDescription(member, UsageViewShortNameLocation.INSTANCE));
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
      final List<PsiElement> callees = computeReferencedCodeSafeToDelete((PsiMember)member);
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
    if (explored instanceof PsiNamedElement) {
      final String name = ((PsiNamedElement)explored).getName();
      if (name != null && 
          PsiSearchHelper.getInstance(explored.getProject())
            .isCheapEnoughToSearch(name, GlobalSearchScope.projectScope(explored.getProject()), null, null) == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) {
        return false;
      }
    }
    if (explored instanceof PsiClassOwner) {
      for (PsiClass aClass : ((PsiClassOwner)explored).getClasses()) {
        if (!usedOnlyIn(aClass, place)) return false;
      }
      return true;
    }
    if (UnusedDeclarationInspectionBase.isDeclaredAsEntryPoint(explored)) return false;
    CommonProcessors.FindProcessor<PsiReference> findProcessor = new CommonProcessors.FindProcessor<>() {
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