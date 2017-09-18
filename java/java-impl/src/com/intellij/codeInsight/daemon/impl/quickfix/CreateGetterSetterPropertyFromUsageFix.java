/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CreateGetterSetterPropertyFromUsageFix extends CreatePropertyFromUsageFix {
  public CreateGetterSetterPropertyFromUsageFix(@NotNull PsiMethodCallExpression methodCall) {
    super(methodCall);
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    boolean available = super.isAvailableImpl(offset);
    if (available) {
      setText("Create property");
    }
    return available;
  }

  @Override
  protected boolean checkTargetClasses(List<PsiClass> classes, String methodName) {
    String propertyName = PropertyUtilBase.getPropertyName(methodName);
    if (propertyName == null) return false;
    String getterName = PropertyUtilBase.suggestGetterName(propertyName, null);
    String setterName = PropertyUtilBase.suggestSetterName(propertyName);
    for (PsiClass aClass : classes) {
      if (aClass.findMethodsByName(getterName, false).length > 0 || aClass.findMethodsByName(setterName, false).length > 0) return false;
    }
    return true;
  }

  @Override
  protected void beforeTemplateFinished(PsiClass aClass, PsiField field) {
    PsiMethod getterPrototype = GenerateMembersUtil.generateSimpleGetterPrototype(field);
    if (aClass.findMethodsBySignature(getterPrototype, false).length == 0) {
      aClass.add(getterPrototype);
    }


    PsiMethod setterPrototype = GenerateMembersUtil.generateSimpleSetterPrototype(field);
    if (aClass.findMethodsBySignature(setterPrototype, false).length == 0) {
      aClass.add(setterPrototype);
    }
    
    super.beforeTemplateFinished(aClass, field);
  }
}
