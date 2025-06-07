// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.codeInsight.daemon.impl.quickfix.SafeDeleteFix;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.ElementDescriptionUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.changeSignature.CallerChooserBase;
import com.intellij.refactoring.changeSignature.MemberNodeBase;
import com.intellij.refactoring.changeSignature.inCallers.JavaMemberNode;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteMemberCalleeUsageInfo;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceJavaDeleteUsageInfo;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewShortNameLocation;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

abstract class SafeDeleteJavaCalleeChooser extends CallerChooserBase<PsiElement> {
  SafeDeleteJavaCalleeChooser(@NotNull PsiMember member,
                              @NotNull Project project,
                              @NotNull List<? super UsageInfo> result) {
    super(member, project, JavaRefactoringBundle.message("safe.delete.select.members.to.propagate.dialog.title"), null, "dummy." + JavaFileType.INSTANCE.getDefaultExtension(), members -> result.addAll(ContainerUtil.map(members, m -> {
      return new SafeDeleteReferenceJavaDeleteUsageInfo(m, m, true);
    })));
  }

  protected abstract @NotNull List<SafeDeleteMemberCalleeUsageInfo> getTopLevelItems();

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
        JavaMemberNode.customizeRendererText(renderer, (PsiMember)member, isEnabled());
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
    protected @Unmodifiable List<PsiElement> computeCallers() {
      PsiElement member = getMember();
      if (getTopMember().equals(member)) {
        return ContainerUtil.map(getTopLevelItems(), info -> info.getCalledElement());
      }

      if (!(member instanceof PsiMember)) return Collections.emptyList();
      final List<PsiElement> callees = SafeDeleteFix.computeReferencedCodeSafeToDelete((PsiMember)member);
      callees.remove(getTopMember());
      return callees;
    }

    @Override
    protected Condition<PsiElement> getFilter() {
      return member -> !getMember().equals(member);
    }
  }
}