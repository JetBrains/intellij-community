/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.beanProperties;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.IntentionWrapper;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.actions.JvmElementActionsFactory;
import com.intellij.lang.jvm.actions.MemberRequest;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.util.ArrayUtil.toObjectArray;

@ApiStatus.Experimental
public class CreateBeanPropertyFixes {

  public static LocalQuickFix[] createFixes(String propertyName,
                                            @NotNull @JvmCommon PsiClass psiClass,
                                            @Nullable PsiType type,
                                            final boolean createSetter) {
    return IntentionWrapper.wrapToQuickFixes(createActions(propertyName, psiClass, type, createSetter), psiClass.getContainingFile());
  }

  public static IntentionAction[] createActions(String propertyName,
                                                @NotNull @JvmCommon PsiClass psiClass,
                                                @Nullable PsiType type,
                                                final boolean createSetter) {
    if (psiClass instanceof PsiCompiledElement) return IntentionAction.EMPTY_ARRAY;
    if (type == null) {
      final Project project = psiClass.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiClass aClass = facade.findClass(JAVA_LANG_STRING, GlobalSearchScope.allScope(project));
      if (aClass == null) return IntentionAction.EMPTY_ARRAY;
      type = facade.getElementFactory().createType(aClass);
    }
    JvmElementActionsFactory factory = JvmElementActionsFactory.forLanguage(psiClass.getLanguage());
    if (factory == null) return IntentionAction.EMPTY_ARRAY;
    return toObjectArray(
      factory.createActions(psiClass, new MemberRequest.Property(propertyName, JvmModifier.PUBLIC, type, createSetter, !createSetter)),
      IntentionAction.class);
  }
}
