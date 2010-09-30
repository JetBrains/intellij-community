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
package com.intellij.psi.util.proximity;

import com.intellij.psi.*;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class ExplicitlyImportedWeigher extends ProximityWeigher {

  public Comparable weigh(@NotNull final PsiElement element, @Nullable final ProximityLocation location) {
    if (location == null) {
      return null;
    }
    if (element instanceof PsiClass) {
      final String qname = ((PsiClass) element).getQualifiedName();
      if (qname != null) {
        final PsiJavaFile psiJavaFile = PsiTreeUtil.getContextOfType(location.getPosition(), PsiJavaFile.class, false);
        if (psiJavaFile != null) {
          final PsiImportList importList = psiJavaFile.getImportList();
          if (importList != null) {
            for (final PsiImportStatement importStatement : importList.getImportStatements()) {
              if (!importStatement.isOnDemand() && qname.equals(importStatement.getQualifiedName())) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }
}
