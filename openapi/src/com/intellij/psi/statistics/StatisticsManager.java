/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.psi.statistics;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.psi.*;

import java.util.Map;

public abstract class StatisticsManager implements SettingsSavingComponent {
  public static NameContext getContext(final PsiElement element) {
    if(element instanceof PsiField){
      if(((PsiField)element).hasModifierProperty(PsiModifier.STATIC) && ((PsiField)element).hasModifierProperty(PsiModifier.FINAL))
        return NameContext.CONSTANT_NAME;
      return NameContext.FIELD_NAME;
    }
    if(element instanceof PsiLocalVariable) {
      return NameContext.LOCAL_VARIABLE_NAME;
    }
    return null;
  }

  public enum NameContext{
    LOCAL_VARIABLE_NAME,
    FIELD_NAME,
    CONSTANT_NAME
  }
  public static StatisticsManager getInstance() {
    return ApplicationManager.getApplication().getComponent(StatisticsManager.class);
  }

  public abstract int getMemberUseCount(PsiType qualifierType, PsiMember member);
  public abstract void incMemberUseCount(PsiType qualifierType, PsiMember member);

  public abstract String[] getNameSuggestions(PsiType type, NameContext context, String prefix);
  public abstract void incNameUseCount(PsiType type, NameContext context, String name);
}
