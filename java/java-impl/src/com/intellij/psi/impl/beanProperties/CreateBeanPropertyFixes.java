// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.beanProperties;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.IntentionWrapper;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.java.beans.PropertyKind;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.lang.jvm.actions.JvmElementActionFactories.createMethodActions;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

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
    final Project project = psiClass.getProject();
    if (type == null) {
      type = JavaPsiFacade.getElementFactory(project).createTypeByFQClassName(JAVA_LANG_STRING, GlobalSearchScope.allScope(project));
    }
    PropertyKind propertyKind = createSetter ? PropertyKind.SETTER : PropertyKind.GETTER;
    CreateBeanPropertyRequest request = new CreateBeanPropertyRequest(psiClass.getProject(), propertyName, propertyKind, type);
    return createMethodActions(psiClass, request).toArray(IntentionAction.EMPTY_ARRAY);
  }
}
