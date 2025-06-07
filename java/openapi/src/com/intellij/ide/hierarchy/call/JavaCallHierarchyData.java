// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy.call;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;
import java.util.Set;

public class JavaCallHierarchyData {
  private final PsiClass myOriginalClass;
  private final PsiMethod myMethodToFind;
  private final PsiClassType myOriginalType;
  private final PsiMethod myMethod;
  private final Set<? extends PsiMethod> myMethodsToFind;
  private final NodeDescriptor<?> myNodeDescriptor;
  private final Map<PsiMember, NodeDescriptor<?>> myResultMap;
  private final Project myProject;

  public JavaCallHierarchyData(@NotNull PsiClass originalClass,
                               @NotNull PsiMethod methodToFind,
                               @NotNull PsiClassType originalType,
                               @NotNull PsiMethod method,
                               @NotNull Set<? extends PsiMethod> methodsToFind,
                               @NotNull NodeDescriptor<?> nodeDescriptor,
                               @NotNull Map<PsiMember, NodeDescriptor<?>> resultMap,
                               @NotNull Project project) {

    myOriginalClass = originalClass;
    myMethodToFind = methodToFind;
    myOriginalType = originalType;
    myMethod = method;
    myMethodsToFind = methodsToFind;
    myNodeDescriptor = nodeDescriptor;
    myResultMap = resultMap;
    myProject = project;
  }

  public @NotNull PsiClass getOriginalClass() {
    return myOriginalClass;
  }

  public @NotNull PsiMethod getMethodToFind() {
    return myMethodToFind;
  }

  public @NotNull PsiClassType getOriginalType() {
    return myOriginalType;
  }

  public @NotNull PsiMethod getMethod() {
    return myMethod;
  }

  public @NotNull @Unmodifiable Set<? extends PsiMethod> getMethodsToFind() {
    return myMethodsToFind;
  }

  public @NotNull NodeDescriptor<?> getNodeDescriptor() {
    return myNodeDescriptor;
  }

  public @NotNull Map<PsiMember, NodeDescriptor<?>> getResultMap() {
    return myResultMap;
  }

  public @NotNull Project getProject() {
    return myProject;
  }
}
