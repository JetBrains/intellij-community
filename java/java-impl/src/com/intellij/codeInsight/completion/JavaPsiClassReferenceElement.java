// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.JavaElementLookupRenderer;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.ClassConditionKey;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Set;

public class JavaPsiClassReferenceElement extends LookupItem<Object> implements TypedLookupItem {
  public static final ClassConditionKey<JavaPsiClassReferenceElement> CLASS_CONDITION_KEY = ClassConditionKey.create(JavaPsiClassReferenceElement.class);
  private final @NotNull PsiClass myClass;
  private final String myQualifiedName;
  private String myForcedPresentableName;
  private final String myPackageDisplayName;
  private final @Nullable Icon myIcon;
  private final boolean myStrikeout;
  private PsiSubstitutor mySubstitutor = PsiSubstitutor.EMPTY;

  public JavaPsiClassReferenceElement(@NotNull PsiClass psiClass) {
    super(psiClass.getName(), psiClass.getName());
    myQualifiedName = psiClass.getQualifiedName();
    myClass = psiClass;
    setInsertHandler(AllClassesGetter.TRY_SHORTENING);
    setTailType(TailTypes.noneType());
    myPackageDisplayName = PsiFormatUtil.getPackageDisplayName(psiClass);
    myIcon = DefaultLookupItemRenderer.getRawIcon(this);
    myStrikeout = JavaElementLookupRenderer.isToStrikeout(this);
  }

  @Override
  public boolean isToStrikeout() {
    return myStrikeout;
  }

  @Override
  public @Nullable Icon getIcon() {
    return myIcon;
  }

  public String getForcedPresentableName() {
    return myForcedPresentableName;
  }

  @Override
  public @Nullable PsiType getType() {
    PsiClass psiClass = getObject();
    return JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass, getSubstitutor());
  }

  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

  public JavaPsiClassReferenceElement setSubstitutor(PsiSubstitutor substitutor) {
    mySubstitutor = substitutor;
    return this;
  }

  @Override
  public @NotNull String getLookupString() {
    if (myForcedPresentableName != null) {
      return myForcedPresentableName;
    }
    return super.getLookupString();
  }

  @Override
  public Set<String> getAllLookupStrings() {
    if (myForcedPresentableName != null) {
      return Collections.singleton(myForcedPresentableName);
    }

    return super.getAllLookupStrings();
  }

  public void setForcedPresentableName(String forcedPresentableName) {
    myForcedPresentableName = forcedPresentableName;
  }

  @Override
  public @NotNull PsiClass getObject() {
    return myClass;
  }

  @Override
  public boolean isValid() {
    return myClass.isValid();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof JavaPsiClassReferenceElement that)) return false;
    if (myQualifiedName != null) {
      return myQualifiedName.equals(that.myQualifiedName);
    }
    return Comparing.equal(myClass, that.myClass);
  }

  public String getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public int hashCode() {
    final String s = myQualifiedName;
    return s == null ? 239 : s.hashCode();
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    renderClassItem(presentation, this, getObject(), false, " " + myPackageDisplayName, mySubstitutor);
  }

  public static void renderClassItem(LookupElementPresentation presentation, LookupElement item, PsiClass psiClass, boolean diamond,
                                     @NotNull String locationString, @NotNull PsiSubstitutor substitutor) {
    if (!(psiClass instanceof PsiTypeParameter)) {
      presentation.setIcon(item instanceof TypedLookupItem typed ? typed.getIcon() : DefaultLookupItemRenderer.getRawIcon(item));
    }

    boolean strikeout = item instanceof TypedLookupItem typed ? typed.isToStrikeout() : JavaElementLookupRenderer.isToStrikeout(item);
    presentation.setItemText(getName(psiClass, item, diamond, substitutor));
    presentation.setStrikeout(strikeout);

    String tailText = locationString;

    if (item instanceof PsiTypeLookupItem typeLookupItem) {
      if (typeLookupItem.isIndicateAnonymous() &&
          (psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) ||
          typeLookupItem.isAddArrayInitializer()) {
        tailText = "{...}" + tailText;
      }
    }
    if (substitutor == PsiSubstitutor.EMPTY && !diamond && psiClass.getTypeParameters().length > 0) {
      String separator = "," + (showSpaceAfterComma(psiClass) ? " " : "");
      tailText = "<" + StringUtil.join(psiClass.getTypeParameters(), PsiTypeParameter::getName, separator) + ">" + tailText;
    }
    presentation.setTailText(tailText, true);
  }

  public String getLocationString() {
    return " " + myPackageDisplayName;
  }

  private static String getName(final PsiClass psiClass, final LookupElement item, boolean diamond, @NotNull PsiSubstitutor substitutor) {
    String forced = item instanceof JavaPsiClassReferenceElement referenceElement ? referenceElement.getForcedPresentableName() :
                    item instanceof PsiTypeLookupItem lookupItem ? lookupItem.getForcedPresentableName() :
                    null;
    if (forced != null) {
      return forced;
    }

    String name = PsiUtilCore.getName(psiClass);
    if (diamond) {
      return name + "<>";
    }

    if (substitutor != PsiSubstitutor.EMPTY) {
      final PsiTypeParameter[] params = psiClass.getTypeParameters();
      if (params.length > 0) {
        return name + formatTypeParameters(substitutor, params);
      }
    }

    return StringUtil.notNullize(name);
  }

  private static @NotNull String formatTypeParameters(final @NotNull PsiSubstitutor substitutor, final PsiTypeParameter[] params) {
    final boolean space = showSpaceAfterComma(params[0]);
    StringBuilder buffer = new StringBuilder();
    buffer.append("<");
    for(int i = 0; i < params.length; i++){
      final PsiTypeParameter param = params[i];
      final PsiType type = substitutor.substitute(param);
      if(type == null){
        return "";
      }
      if (type instanceof PsiClassType classType && classType.getParameters().length > 0) {
        buffer.append(classType.rawType().getPresentableText()).append("<...>");
      } else {
        buffer.append(type.getPresentableText());
      }

      if(i < params.length - 1) {
        buffer.append(",");
        if (space) {
          buffer.append(" ");
        }
      }
    }
    buffer.append(">");
    return buffer.toString();
  }

  private static boolean showSpaceAfterComma(PsiClass element) {
    return CodeStyle.getLanguageSettings(element.getContainingFile(), JavaLanguage.INSTANCE).SPACE_AFTER_COMMA;
  }

  static boolean isInaccessibleConstructorSuggestion(@NotNull PsiElement position, @Nullable PsiClass cls) {
    if (cls == null || cls.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
    PsiMethod[] constructors = cls.getConstructors();
    if (constructors.length > 0) {
      return !ContainerUtil.exists(constructors, ctor ->
        JavaResolveUtil.isAccessible(ctor, cls, ctor.getModifierList(), position, null, null));
    }
    return false;
  }
}
