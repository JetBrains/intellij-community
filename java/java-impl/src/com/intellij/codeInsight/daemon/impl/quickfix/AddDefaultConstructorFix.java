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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.GenerateConstructorHandler;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;

public class AddDefaultConstructorFix extends AddMethodFix {
  public AddDefaultConstructorFix(PsiClass aClass) {
    super(generateConstructor(aClass), aClass);
    setText(QuickFixBundle.message("add.default.constructor.text", aClass.getName()));
  }

  private static String generateConstructor(PsiClass aClass) {
    String constructorModifier = GenerateConstructorHandler.getConstructorModifier(aClass);
    if (constructorModifier == PsiModifier.PACKAGE_LOCAL) {
      return aClass.getName() + "() {}";
    }
    return constructorModifier + " " + aClass.getName() + "() {}";
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.default.constructor.family");
  }
}
