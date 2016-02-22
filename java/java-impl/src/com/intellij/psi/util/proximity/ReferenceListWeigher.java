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
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.ProximityLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.util.proximity.ReferenceListWeigher.ReferenceListApplicability.*;

/**
 * @author peter
 */
public class ReferenceListWeigher extends ProximityWeigher {
  public static final ReferenceListWeigher INSTANCE = new ReferenceListWeigher();

  public static final ElementPattern<PsiElement> INSIDE_REFERENCE_LIST =
    PlatformPatterns.psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiReferenceList.class);

  protected enum Preference {
    Interfaces, Classes, Exceptions
  }

  @Nullable
  protected Preference getPreferredCondition(@NotNull final PsiElement position) {
    if (INSIDE_REFERENCE_LIST.accepts(position)) {
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

  public enum ReferenceListApplicability {
    inapplicable,
    unknown,
    applicableByKind,
    applicableByName
  }

  @Override
  public ReferenceListApplicability weigh(@NotNull PsiElement element, @NotNull ProximityLocation location) {
    if (element instanceof PsiClass && location.getPosition() != null) {
      return getApplicability((PsiClass)element, location.getPosition());
    }
    return unknown;
  }

  @NotNull
  public ReferenceListApplicability getApplicability(@NotNull PsiClass aClass, @NotNull PsiElement position) {
    Preference condition = getPreferredCondition(position);
    if (condition == Preference.Interfaces) return aClass.isInterface() ? applicableByKind : inapplicable;
    if (condition == Preference.Classes) {
      if (aClass.isInterface()) return inapplicable;
      String name = aClass.getName();
      if (name != null && name.endsWith("TestCase")) {
        VirtualFile vFile = aClass.getContainingFile().getVirtualFile();
        if (vFile != null && ProjectFileIndex.SERVICE.getInstance(aClass.getProject()).isInTestSourceContent(vFile)) {
          return applicableByName;
        }
      }
      return applicableByKind;
    }
    if (condition == Preference.Exceptions) {
      return InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_THROWABLE) ? applicableByKind : inapplicable;
    }
    return unknown;
  }
}
