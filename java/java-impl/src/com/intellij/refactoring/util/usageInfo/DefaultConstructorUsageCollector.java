package com.intellij.refactoring.util.usageInfo;

import com.intellij.psi.*;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;

import java.util.ArrayList;

public class DefaultConstructorUsageCollector implements RefactoringUtil.ImplicitConstructorUsageVisitor {
  private final ArrayList<UsageInfo> myUsages;

  public void visitConstructor(PsiMethod constructor, PsiMethod baseConstructor) {
    myUsages.add(new DefaultConstructorImplicitUsageInfo(constructor, baseConstructor));
  }

  public void visitClassWithoutConstructors(PsiClass aClass) {
    myUsages.add(new NoConstructorClassUsageInfo(aClass));
  }

  public DefaultConstructorUsageCollector(ArrayList<UsageInfo> result) {
    myUsages = result;
  }
}
