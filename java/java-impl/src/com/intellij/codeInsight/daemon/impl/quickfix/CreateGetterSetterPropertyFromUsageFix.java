// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PropertyUtilBase;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Deprecated
@ScheduledForRemoval(inVersion = "2019.3")
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
  protected boolean checkTargetClasses(List<? extends PsiClass> classes, String methodName) {
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
