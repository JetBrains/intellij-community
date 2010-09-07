/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature.inCallers;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.changeSignature.CallerChooserBase;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Consumer;
import com.intellij.util.containers.HashSet;

import java.util.Set;

public class JavaCallerChooser extends CallerChooserBase<PsiMethod> {

  public JavaCallerChooser(PsiMethod method, Project project, String title, Tree previousTree, Consumer<Set<PsiMethod>> callback) {
    super(method, project, title, previousTree, callback);
  }

  @Override
  protected JavaMethodNode createTreeNode(PsiMethod method, HashSet<PsiMethod> called, Runnable cancelCallback) {
    return new JavaMethodNode(method, called, myProject, cancelCallback);
  }

  @Override
  protected PsiMethod[] findDeepestSuperMethods(PsiMethod method) {
    return method.findDeepestSuperMethods();
  }
}
