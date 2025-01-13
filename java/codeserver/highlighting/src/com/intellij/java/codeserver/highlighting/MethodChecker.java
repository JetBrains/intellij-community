// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeError;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.MostlySingularMultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MethodChecker {
  private final @NotNull JavaErrorVisitor myVisitor;
  private final @NotNull Map<PsiClass, MostlySingularMultiMap<MethodSignature, PsiMethod>> myDuplicateMethods = new HashMap<>();
  private static final MethodSignature ourValuesEnumSyntheticMethod =
    MethodSignatureUtil.createMethodSignature("values",
                                              PsiType.EMPTY_ARRAY,
                                              PsiTypeParameter.EMPTY_ARRAY,
                                              PsiSubstitutor.EMPTY);

  MethodChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  private @NotNull MostlySingularMultiMap<MethodSignature, PsiMethod> getDuplicateMethods(@NotNull PsiClass aClass) {
    MostlySingularMultiMap<MethodSignature, PsiMethod> signatures = myDuplicateMethods.get(aClass);
    if (signatures == null) {
      signatures = new MostlySingularMultiMap<>();
      for (PsiMethod method : aClass.getMethods()) {
        if (method instanceof ExternallyDefinedPsiElement) continue; // ignore aspectj-weaved methods; they are checked elsewhere
        MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
        signatures.add(signature, method);
      }

      myDuplicateMethods.put(aClass, signatures);
    }
    return signatures;
  }

  void checkMustBeThrowable(@NotNull PsiClass aClass, @NotNull PsiElement context) {
    PsiClassType type = JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
    PsiClassType throwable = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_THROWABLE, context.getResolveScope());
    if (!TypeConversionUtil.isAssignable(throwable, type)) {
      if (IncompleteModelUtil.isIncompleteModel(context) && IncompleteModelUtil.isPotentiallyConvertible(throwable, type, context)) return;
      myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(context, new JavaIncompatibleTypeError(throwable, type)));
    }
  }

  private static boolean isEnumSyntheticMethod(@NotNull MethodSignature methodSignature, @NotNull Project project) {
    if (methodSignature.equals(ourValuesEnumSyntheticMethod)) return true;
    PsiType javaLangString = PsiType.getJavaLangString(PsiManager.getInstance(project), GlobalSearchScope.allScope(project));
    MethodSignature valueOfMethod = MethodSignatureUtil.createMethodSignature("valueOf", new PsiType[]{javaLangString},
                                                                              PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
    return MethodSignatureUtil.areSignaturesErasureEqual(valueOfMethod, methodSignature);
  }

  void checkDuplicateMethod(@NotNull PsiClass aClass, @NotNull PsiMethod method) {
    if (method instanceof ExternallyDefinedPsiElement) return;
    MostlySingularMultiMap<MethodSignature, PsiMethod> duplicateMethods = getDuplicateMethods(aClass);
    MethodSignature methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
    int methodCount = 1;
    List<PsiMethod> methods = (List<PsiMethod>)duplicateMethods.get(methodSignature);
    if (methods.size() > 1) {
      methodCount++;
    }

    if (methodCount == 1 && aClass.isEnum() && isEnumSyntheticMethod(methodSignature, aClass.getProject())) {
      methodCount++;
    }
    if (methodCount > 1) {
      myVisitor.report(JavaErrorKinds.METHOD_DUPLICATE.create(method));
    }
  }
}
