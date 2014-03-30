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

import java.util.Map;
import java.util.Set;

/**
 * Created by Max Medvedev on 10/5/13
 */
public class JavaCallHierarchyData {
  private final PsiClass myOriginalClass;
  private final PsiMethod myMethodToFind;
  private final PsiClassType myOriginalType;
  private final PsiMethod myMethod;
  private final Set<PsiMethod> myMethodsToFind;
  private final NodeDescriptor myNodeDescriptor;
  private final Map<PsiMember, NodeDescriptor> myResultMap;
  private final Project myProject;

  public JavaCallHierarchyData(PsiClass originalClass,
                               PsiMethod methodToFind,
                               PsiClassType originalType,
                               PsiMethod method,
                               Set<PsiMethod> methodsToFind,
                               NodeDescriptor nodeDescriptor,
                               Map<PsiMember, NodeDescriptor> resultMap,
                               Project project) {

    myOriginalClass = originalClass;
    myMethodToFind = methodToFind;
    myOriginalType = originalType;
    myMethod = method;
    myMethodsToFind = methodsToFind;
    myNodeDescriptor = nodeDescriptor;
    myResultMap = resultMap;
    myProject = project;
  }

  public PsiClass getOriginalClass() {
    return myOriginalClass;
  }

  public PsiMethod getMethodToFind() {
    return myMethodToFind;
  }

  public PsiClassType getOriginalType() {
    return myOriginalType;
  }

  public PsiMethod getMethod() {
    return myMethod;
  }

  public Set<PsiMethod> getMethodsToFind() {
    return myMethodsToFind;
  }

  public NodeDescriptor getNodeDescriptor() {
    return myNodeDescriptor;
  }

  public Map<PsiMember, NodeDescriptor> getResultMap() {
    return myResultMap;
  }

  public Project getProject() {
    return myProject;
  }
}
