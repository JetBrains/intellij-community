/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.ProximityLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class ReferenceListWeigher extends ProximityWeigher {

  private static final PsiElementPattern.Capture<PsiElement> INSIDE_REFERENCE_LIST =
    PlatformPatterns.psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiReferenceList.class);

  protected enum Preference {
    Interfaces, Classes, Exceptions
  }

  @Nullable
  protected Preference getPreferredCondition(@NotNull ProximityLocation location) {
    PsiElement position = location.getPosition();
    if (INSIDE_REFERENCE_LIST.accepts(position)) {
      assert position != null;
      PsiReferenceList list = (PsiReferenceList)position.getParent().getParent();
      PsiReferenceList.Role role = list.getRole();
      if (shouldContainInterfaces(list, role)) {
        return Preference.Interfaces;
      }
      if (role == PsiReferenceList.Role.EXTENDS_LIST) {
        return Preference.Classes;
      }
      if (role == PsiReferenceList.Role.THROWS_LIST) {
        return Preference.Exceptions;

      }
    }
    return null;
  }

  private static boolean shouldContainInterfaces(PsiReferenceList list, PsiReferenceList.Role role) {
    if (role == PsiReferenceList.Role.EXTENDS_LIST) {
      PsiElement parent = list.getParent();
      return parent instanceof PsiClass && ((PsiClass)parent).isInterface();
    }
    if (role == PsiReferenceList.Role.IMPLEMENTS_LIST) {
      return true;
    }
    return false;
  }

  @Override
  public Integer weigh(@NotNull PsiElement element, @NotNull ProximityLocation location) {
    if (element instanceof PsiClass) {
      Preference condition = getPreferredCondition(location);
      PsiClass aClass = (PsiClass)element;
      if (condition == Preference.Interfaces) return aClass.isInterface() ? 1 : -1;
      if (condition == Preference.Classes) {
        if (aClass.isInterface()) return -1;
        if (aClass.getName().endsWith("TestCase")) {
          VirtualFile vFile = aClass.getContainingFile().getVirtualFile();
          if (vFile != null && ProjectFileIndex.SERVICE.getInstance(location.getProject()).isInTestSourceContent(vFile)) {
            return 2;
          }
        }
        return 1;
      }
      if (condition == Preference.Exceptions) return InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_THROWABLE) ? 1 : -1;
    }

    return 0;
  }
}
