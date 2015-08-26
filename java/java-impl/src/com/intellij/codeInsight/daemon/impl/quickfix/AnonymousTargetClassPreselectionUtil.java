/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class AnonymousTargetClassPreselectionUtil {
  private static final String PRESELECT_ANONYMOUS = "create.member.preselect.anonymous";

  public static void rememberSelection(PsiClass aClass, PsiClass firstClass) {
    if (firstClass instanceof PsiAnonymousClass) {
      PropertiesComponent.getInstance().setValue(PRESELECT_ANONYMOUS, aClass == firstClass);
    }
  }

  @Nullable
  public static PsiClass getPreselection(Collection<PsiClass> classes, PsiClass firstClass) {
    if (firstClass instanceof PsiAnonymousClass && !PropertiesComponent.getInstance().getBoolean(PRESELECT_ANONYMOUS, true)) {
      for (PsiClass aClass : classes) {
        if (!(aClass instanceof PsiAnonymousClass)) {
          return aClass;
        }
      }
    }
    return null;
  }
}
