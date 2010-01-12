/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.typeCook.deductive.util;

import com.intellij.psi.*;

/**
 * @author db
 */
public abstract class Visitor extends JavaRecursiveElementWalkingVisitor {
  @Override public void visitPackage(final PsiPackage aPackage) {
    final PsiDirectory[] dirs = aPackage.getDirectories();

    for (PsiDirectory dir : dirs) {
      final PsiFile[] files = dir.getFiles();

      for (final PsiFile file : files) {
        if (file instanceof PsiJavaFile) {
          super.visitJavaFile(((PsiJavaFile)file));
        }
      }
    }
  }
}
