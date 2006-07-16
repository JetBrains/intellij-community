package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author ven
 */
abstract class GenericsInspectionToolBase extends BaseLocalInspectionTool {
  public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    if (initializers.length == 0) return null;
    List<ProblemDescriptor> descriptors = new ArrayList<ProblemDescriptor>();
    for (PsiClassInitializer initializer : initializers) {
      final ProblemDescriptor[] localDescriptions = getDescriptions(initializer, manager);
      if (localDescriptions != null) {
        descriptors.addAll(Arrays.asList(localDescriptions));
      }
    }
    if (descriptors.isEmpty()) return null;
    return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }

  public ProblemDescriptor[] checkField(PsiField field, InspectionManager manager, boolean isOnTheFly) {
    final PsiExpression initializer = field.getInitializer();
    if (initializer != null) {
      return getDescriptions(initializer, manager);
    }
    return null;
  }

  public ProblemDescriptor[] checkMethod(PsiMethod psiMethod, InspectionManager manager, boolean isOnTheFly) {
    final PsiCodeBlock body = psiMethod.getBody();
    if (body != null) {
      return getDescriptions(body, manager);
    }
    return null;
  }

  public abstract ProblemDescriptor[] getDescriptions(PsiElement place, InspectionManager manager);
}
