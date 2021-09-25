/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.hierarchy.call;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

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

  @NotNull
  public PsiClass getOriginalClass() {
    return myOriginalClass;
  }

  @NotNull
  public PsiMethod getMethodToFind() {
    return myMethodToFind;
  }

  @NotNull
  public PsiClassType getOriginalType() {
    return myOriginalType;
  }

  @NotNull
  public PsiMethod getMethod() {
    return myMethod;
  }

  @NotNull
  public Set<? extends PsiMethod> getMethodsToFind() {
    return myMethodsToFind;
  }

  @NotNull
  public NodeDescriptor<?> getNodeDescriptor() {
    return myNodeDescriptor;
  }

  @NotNull
  public Map<PsiMember, NodeDescriptor<?>> getResultMap() {
    return myResultMap;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }
}
