/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.execution.configurations;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiMethodUtil;

/**
 * User: anna
 * Date: Jan 26, 2005
 */
public class ConfigurationUtil {
  public static Condition<PsiClass> PUBLIC_INSTANTIATABLE_CLASS = new Condition<PsiClass>() {
    public boolean value(final PsiClass psiClass) {
      if (!MAIN_CLASS.value(psiClass)) return false;
      if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
      if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) return false;
      return true;
    }
  };
  public static Condition<PsiClass> MAIN_CLASS = PsiMethodUtil.MAIN_CLASS;
}
