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

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.ProximityLocation;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class KnownElementWeigher extends ProximityWeigher {

  public Comparable weigh(@NotNull final PsiElement element, @NotNull final ProximityLocation location) {
    if (element instanceof PsiClass) {
      @NonNls final String qname = ((PsiClass)element).getQualifiedName();
      if (qname != null) {
        if (qname.startsWith("java.lang")) return 4;
        if (qname.startsWith("java.util")) return 3;
        if (qname.startsWith("java.")) return 2;
        if (qname.startsWith("javax.")) return 1;
        if (qname.startsWith("com.")) return -1;
        if (qname.startsWith("net.")) return -1;
      }
    }
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      if ("finalize".equals(method.getName()) || "registerNatives".equals(method.getName())) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
          return -1;
        }
      }
      if ("subSequence".equals(method.getName())) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && CommonClassNames.JAVA_LANG_STRING.equals(containingClass.getQualifiedName())) {
          return -1;
        }
      }
    }
    return 0;
  }
}