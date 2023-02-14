// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.changeSignature.inCallers;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.changeSignature.MemberNodeBase;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nls;

import java.util.HashSet;
import java.util.Set;

public class JavaCallerChooser extends AbstractJavaMemberCallerChooser<PsiMethod> {
  public JavaCallerChooser(PsiMethod method,
                           Project project,
                           @Nls(capitalization = Nls.Capitalization.Title) String title,
                           Tree previousTree,
                           Consumer<? super Set<PsiMethod>> callback) {
    super(method, project, title, previousTree, callback);
  }

  @Override
  protected @NlsContexts.Label String getEmptyCalleeText() {
    return JavaRefactoringBundle.message("changeSignature.empty.callee.method.text");
  }

  @Override
  protected @NlsContexts.Label String getEmptyCallerText() {
    return JavaRefactoringBundle.message("changeSignature.empty.caller.method.text");
  }

  @Override
  protected MemberNodeBase<PsiMethod> createTreeNodeFor(PsiMethod method, HashSet<PsiMethod> called, Runnable cancelCallback) {
    return new JavaMethodNode(method, called, myProject, cancelCallback);
  }

  @Override
  protected PsiMethod[] findDeepestSuperMethods(PsiMethod method) {
    return method.findDeepestSuperMethods();
  }
}
