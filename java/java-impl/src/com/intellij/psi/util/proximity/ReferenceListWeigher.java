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

import com.intellij.openapi.util.Condition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.ProximityLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class ReferenceListWeigher extends ProximityWeigher {

  protected static final Condition<PsiClass> PREFER_INTERFACES = new Condition<PsiClass>() {
    @Override
    public boolean value(PsiClass psiClass) {
      return psiClass.isInterface();
    }
  };
  protected static final Condition<PsiClass> PREFER_CLASSES = new Condition<PsiClass>() {
    @Override
    public boolean value(PsiClass psiClass) {
      return !psiClass.isInterface();
    }
  };
  protected static final Condition<PsiClass> PREFER_EXCEPTIONS = new Condition<PsiClass>() {
    @Override
    public boolean value(PsiClass psiClass) {
      return InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_LANG_THROWABLE);
    }
  };

  @Nullable
  protected Condition<PsiClass> getPreferredCondition(@NotNull ProximityLocation location) {
    PsiElement position = location.getPosition();
    if (PlatformPatterns.psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiReferenceList.class).accepts(position)) {
      assert position != null;
      PsiReferenceList list = (PsiReferenceList)position.getParent().getParent();
      PsiReferenceList.Role role = list.getRole();
      if (shouldContainInterfaces(list, role)) {
        return PREFER_INTERFACES;
      }
      if (role == PsiReferenceList.Role.EXTENDS_LIST) {
        return PREFER_CLASSES;
      }
      if (role == PsiReferenceList.Role.THROWS_LIST) {
        return PREFER_EXCEPTIONS;

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
      Condition<PsiClass> condition = getPreferredCondition(location);
      if (condition != null) {
        return condition.value((PsiClass)element) ? 1 : -1;
      }
    }

    return 0;
  }
}
