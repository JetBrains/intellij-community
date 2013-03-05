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
package com.intellij.codeInsight.generation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PropertyUtil;

/**
 * User: anna
 * Date: 3/4/13
 */
public abstract class GetterSetterPrototypeProvider {
  public static final ExtensionPointName<GetterSetterPrototypeProvider> EP_NAME = ExtensionPointName.create("com.intellij.getterSetterProvider");
  public abstract boolean canGeneratePrototypeFor(PsiField field);
  public abstract PsiMethod[] generateGetters(PsiField field); 
  public abstract PsiMethod[] generateSetters(PsiField field);

  public abstract boolean isReadOnly(PsiField field);

  public static PsiMethod[] generateGetterSetters(PsiField field, boolean generateGetter) {
    for (GetterSetterPrototypeProvider provider : Extensions.getExtensions(EP_NAME)) {
      if (provider.canGeneratePrototypeFor(field)) {
        return generateGetter ? provider.generateGetters(field) : provider.generateSetters(field);
      }
    }
    return new PsiMethod[] {generateGetter ? PropertyUtil.generateGetterPrototype(field) : PropertyUtil.generateSetterPrototype(field)};
  }

  public static boolean isReadOnlyProperty(PsiField field) {
    for (GetterSetterPrototypeProvider provider : Extensions.getExtensions(EP_NAME)) {
      if (provider.canGeneratePrototypeFor(field)) {
        return provider.isReadOnly(field);
      }
    }
    return field.hasModifierProperty(PsiModifier.FINAL);
  }
}
