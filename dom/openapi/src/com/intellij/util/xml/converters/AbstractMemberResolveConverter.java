package com.intellij.util.xml.converters;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyMemberType;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Gregory.Shrago
 */
public abstract class AbstractMemberResolveConverter extends ResolvingConverter<PsiMember> {
  @Nullable
  protected abstract PsiClass getTargetClass(final ConvertContext context);

  @NotNull
  protected abstract PropertyMemberType[] getMemberTypes(final ConvertContext context);

  @NotNull
  protected PsiType getPsiType(final ConvertContext context) {
    return PsiType.getJavaLangObject(context.getPsiManager(), context.getPsiManager().getProject().getAllScope());
  }

  protected boolean isLookDeep() {
    return true;
  }

  protected boolean isPropertyNameUsed() {
    return true;
  }

  public PsiMember fromString(final String s, final ConvertContext context) {
    if (s == null) return null;
    final PsiClass psiClass = getTargetClass(context);
    if (psiClass == null) return null;
    final String propertyName = isPropertyNameUsed() ? s : PropertyUtil.getPropertyName(s);
    for (PropertyMemberType type : getMemberTypes(context)) {
      switch (type) {
        case FIELD:
          final PsiField field = psiClass.findFieldByName(propertyName, isLookDeep());
          if (field != null) return field;
          break;
        case GETTER:
          final PsiMethod getter = PropertyUtil.findPropertyGetter(psiClass, propertyName, false, isLookDeep());
          if (getter != null) return getter;
          break;
        case SETTER:
          final PsiMethod setter = PropertyUtil.findPropertySetter(psiClass, propertyName, false, isLookDeep());
          if (setter != null) return setter;
          break;
      }
    }
    return null;
  }



  public String toString(final PsiMember t, final ConvertContext context) {
    return t == null? null : isPropertyNameUsed()? PropertyUtil.getPropertyName(t) : t.getName();
  }

  public String getErrorMessage(final String s, final ConvertContext context) {
    final DomElement parent = context.getInvocationElement().getParent();
    assert parent != null;
    return CodeInsightBundle.message("error.cannot.resolve.0.1", ElementPresentationManager.getTypeName(parent.getClass()), s);
  }

  @NotNull
  public Collection<? extends PsiMember> getVariants(final ConvertContext context) {
    final PsiClass psiClass = getTargetClass(context);
    if (psiClass == null) return Collections.emptyList();

    final ArrayList<PsiMember> list = new ArrayList<PsiMember>();
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
    return !psiMethod.isConstructor() && !psiMethod.hasModifierProperty(PsiModifier.STATIC) && PropertyUtil.getPropertyName(psiMethod) != null;
  }

  protected boolean fieldSuits(final PsiField psiField) {
    return !psiField.hasModifierProperty(PsiModifier.STATIC);
  }

  public LocalQuickFix[] getQuickFixes(final ConvertContext context) {
    final String targetName = ((GenericValue)context.getInvocationElement()).getStringValue();
    if (targetName == null) return super.getQuickFixes(context);
    final PsiClass targetClass = getTargetClass(context);
    if (targetClass == null) return super.getQuickFixes(context);
    final PropertyMemberType memberType = getMemberTypes(context)[0];

    final PsiType psiType = getPsiType(context);
    final IntentionAction fix = QuickFixFactory.getInstance().createCreateFieldOrPropertyFix(targetClass, targetName, psiType, memberType);
    return fix instanceof LocalQuickFix? new LocalQuickFix[] {(LocalQuickFix)fix} : super.getQuickFixes(context);
  }

  public void handleElementRename(final GenericDomValue<PsiMember> genericValue, final ConvertContext context, final String newElementName) {
    super.handleElementRename(genericValue, context, isPropertyNameUsed()? PropertyUtil.getPropertyName(newElementName) : newElementName);
  }

  public void bindReference(final GenericDomValue<PsiMember> genericValue, final ConvertContext context, final PsiElement newTarget) {
    if (newTarget instanceof PsiMember) {
      final String elementName = ((PsiMember)newTarget).getName();
      genericValue.setStringValue(isPropertyNameUsed() ? PropertyUtil.getPropertyName(elementName) : elementName);
    }
  }
}