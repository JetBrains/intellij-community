// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class MemberLookupHelper {
  private final PsiMember myMember;
  private final boolean myMergedOverloads;
  private final @Nullable PsiClass myContainingClass;
  private boolean myShouldImport;

  public MemberLookupHelper(@NotNull List<? extends PsiMethod> overloads,
                            @Nullable PsiClass containingClass,
                            boolean shouldImport) {
    this(overloads.getFirst(), containingClass, shouldImport, true);
  }

  public MemberLookupHelper(@NotNull PsiMember member,
                            @Nullable PsiClass containingClass,
                            boolean shouldImport,
                            final boolean mergedOverloads) {
    myMember = member;
    myContainingClass = containingClass;
    myShouldImport = shouldImport;
    myMergedOverloads = mergedOverloads;
  }

  public @NotNull PsiMember getMember() {
    return myMember;
  }

  public @Nullable PsiClass getContainingClass() {
    return myContainingClass;
  }

  public void setShouldBeImported(boolean shouldImportStatic) {
    myShouldImport = shouldImportStatic;
  }

  public boolean willBeImported() {
    return myShouldImport;
  }
  
  public boolean isMergedOverloads() {
    return myMergedOverloads;
  }

  public void renderElement(@NotNull LookupElementPresentation presentation,
                            boolean showClass,
                            boolean showPackage,
                            @NotNull PsiSubstitutor substitutor) {
    final String className = myContainingClass == null ? "???" : myContainingClass.getName();

    final String memberName = myMember.getName();
    boolean constructor = myMember instanceof PsiMethod method && method.isConstructor();
    if (constructor) {
      presentation.setItemText("new " + memberName);
      if (myContainingClass != null && myContainingClass.getTypeParameters().length > 0) {
        presentation.appendTailText("<>", false);
      }
    }
    else if (showClass && StringUtil.isNotEmpty(className)) {
      presentation.setItemText(className + "." + memberName);
    }
    else {
      presentation.setItemText(memberName);
    }

    final String qname = myContainingClass == null ? "" : myContainingClass.getQualifiedName();
    String pkg = qname == null ? "" : StringUtil.getPackageName(qname);
    String location = showPackage && StringUtil.isNotEmpty(pkg) ? " (" + pkg + ")" : "";

    final String params = myMergedOverloads
                          ? "(...)"
                          : myMember instanceof PsiMethod method
                            ? getMethodParameterString(method, substitutor)
                            : "";

    presentation.appendTailText(params, false);
    if (myShouldImport && !constructor && StringUtil.isNotEmpty(className)) {
      presentation.appendTailText(JavaBundle.message("member.in.class", className) + location, true);
    } else {
      presentation.appendTailText(location, true);
    }

    PsiType type = getDeclaredType(myMember, substitutor);
    if (type != null) {
      presentation.setTypeText(type.getPresentableText());
    }
  }

  static @Nullable PsiType getDeclaredType(PsiMember member, PsiSubstitutor substitutor) {
    if (member instanceof PsiField field) {
      return substitutor.substitute(field.getType());
    }
    if (member instanceof PsiMethod method) {
      if (method.isConstructor()) {
        PsiClass aClass = Objects.requireNonNull(method.getContainingClass());
        return JavaPsiFacade.getElementFactory(method.getProject()).createType(aClass, substitutor);
      }
      return patchGetClass(method, substitutor.substitute(method.getReturnType()));
    }
    return null;
  }

  @ApiStatus.Internal
  public static @Nullable PsiType patchGetClass(@NotNull PsiMethod method, @Nullable PsiType type) {
    if (PsiTypesUtil.isGetClass(method) && type instanceof PsiClassType classType) {
      PsiType arg = ContainerUtil.getFirstItem(Arrays.asList(classType.getParameters()));
      PsiType bound = arg instanceof PsiWildcardType wildcardType ? TypeConversionUtil.erasure(wildcardType.getExtendsBound()) : null;
      if (bound != null) {
        return PsiTypesUtil.createJavaLangClassType(method, bound, false);
      }
    }
    return type;
  }

  static @NotNull String getMethodParameterString(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor) {
    return PsiFormatUtil.formatMethod(method, substitutor,
                                      PsiFormatUtilBase.SHOW_PARAMETERS, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE);
  }
}
