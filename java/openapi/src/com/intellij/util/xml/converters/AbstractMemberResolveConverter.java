// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.converters;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.ide.TypePresentationService;
import com.intellij.psi.*;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PropertyMemberType;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Gregory.Shrago
 */
public abstract class AbstractMemberResolveConverter extends ResolvingConverter<PsiMember> {
  protected abstract @Nullable PsiClass getTargetClass(final ConvertContext context);

  protected abstract PropertyMemberType @NotNull [] getMemberTypes(final ConvertContext context);

  protected @NotNull PsiType getPsiType(final ConvertContext context) {
    return PsiType.getJavaLangObject(context.getPsiManager(), ProjectScope.getAllScope(context.getPsiManager().getProject()));
  }

  protected boolean isLookDeep() {
    return true;
  }

  protected String getPropertyName(final String s, final ConvertContext context) {
    return s;
  }

  @Override
  public PsiMember fromString(final String s, final @NotNull ConvertContext context) {
    if (s == null) return null;
    final PsiClass psiClass = getTargetClass(context);
    if (psiClass == null) return null;
    for (PropertyMemberType type : getMemberTypes(context)) {
      PsiMember member = switch (type) {
        case FIELD -> psiClass.findFieldByName(s, isLookDeep());
        case GETTER -> PropertyUtilBase.findPropertyGetter(psiClass, getPropertyName(s, context), false, isLookDeep());
        case SETTER -> PropertyUtilBase.findPropertySetter(psiClass, getPropertyName(s, context), false, isLookDeep());
      };
      if (member != null) return member;
    }
    return null;
  }


  @Override
  public String toString(final PsiMember t, final @NotNull ConvertContext context) {
    return t == null? null : getPropertyName(t.getName(), context);
  }

  @Override
  public String getErrorMessage(final String s, final @NotNull ConvertContext context) {
    final DomElement parent = context.getInvocationElement().getParent();
    assert parent != null;
    return CodeInsightBundle.message("error.cannot.resolve.0.1", TypePresentationService.getService().getTypeName(parent), s);
  }

  @Override
  public @NotNull @Unmodifiable Collection<? extends PsiMember> getVariants(final @NotNull ConvertContext context) {
    final PsiClass psiClass = getTargetClass(context);
    if (psiClass == null) return Collections.emptyList();

    final ArrayList<PsiMember> list = new ArrayList<>();
    for (PsiField psiField : isLookDeep()? psiClass.getAllFields() : psiClass.getFields()) {
      if (fieldSuits(psiField)) {
        list.add(psiField);
      }
    }
    for (PsiMethod psiMethod : isLookDeep()? psiClass.getAllMethods() : psiClass.getMethods()) {
      if (methodSuits(psiMethod)) {
        list.add(psiMethod);
      }
    }
    return list;
  }

  protected boolean methodSuits(final PsiMethod psiMethod) {
    return !psiMethod.isConstructor() && !psiMethod.hasModifierProperty(PsiModifier.STATIC) && PropertyUtilBase.getPropertyName(psiMethod) != null;
  }

  protected boolean fieldSuits(final PsiField psiField) {
    return !psiField.hasModifierProperty(PsiModifier.STATIC);
  }

  @Override
  public LocalQuickFix[] getQuickFixes(final @NotNull ConvertContext context) {
    final String targetName = ((GenericValue<?>)context.getInvocationElement()).getStringValue();
    if (!PsiNameHelper.getInstance(context.getProject()).isIdentifier(targetName)) return super.getQuickFixes(context);
    final PsiClass targetClass = getTargetClass(context);
    if (targetClass == null) return super.getQuickFixes(context);
    final PropertyMemberType memberType = getMemberTypes(context)[0];

    final PsiType psiType = getPsiType(context);
    final IntentionAction fix = QuickFixFactory.getInstance().createCreateFieldOrPropertyFix(targetClass, targetName, psiType, memberType);
    return fix instanceof LocalQuickFix? new LocalQuickFix[] {(LocalQuickFix)fix} : super.getQuickFixes(context);
  }

  @Override
  public void handleElementRename(final GenericDomValue<PsiMember> genericValue, final ConvertContext context, final String newElementName) {
    super.handleElementRename(genericValue, context, getPropertyName(newElementName, context));
  }

  @Override
  public void bindReference(final GenericDomValue<PsiMember> genericValue, final ConvertContext context, final PsiElement newTarget) {
    if (newTarget instanceof PsiMember) {
      final String elementName = ((PsiMember)newTarget).getName();
      genericValue.setStringValue(getPropertyName(elementName, context));
    }
  }
}