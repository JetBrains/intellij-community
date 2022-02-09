// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFixBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

import static com.intellij.openapi.util.NullableLazyValue.volatileLazyNullable;

public class ExpectedTypeInfoImpl implements ExpectedTypeInfo {
  public static final Supplier<String> NULL = () -> null;

  private final @NotNull PsiType myType;
  private final @NotNull PsiType myDefaultType;
  private final int myKind;
  private final @NotNull TailType myTailType;
  private final PsiMethod myCalledMethod;
  private final @NotNull Supplier<String> expectedNameComputable;
  private final @NotNull NullableLazyValue<String> expectedNameLazyValue;

  public ExpectedTypeInfoImpl(@NotNull PsiType type,
                              @Type int kind,
                              @NotNull PsiType defaultType,
                              @NotNull TailType tailType,
                              PsiMethod calledMethod,
                              @NotNull Supplier<String> expectedName) {
    myType = type;
    myKind = kind;

    myTailType = tailType;
    myDefaultType = defaultType;
    myCalledMethod = calledMethod;
    expectedNameComputable = expectedName;
    expectedNameLazyValue = volatileLazyNullable(expectedNameComputable);

    PsiUtil.ensureValidType(type);
    PsiUtil.ensureValidType(defaultType);
  }

  @Override
  public int getKind() {
    return myKind;
  }

  @NotNull
  @Override
  public TailType getTailType() {
    return myTailType;
  }

  @Nullable
  public String getExpectedName() {
    return expectedNameLazyValue.getValue();
  }

  @Override
  public PsiMethod getCalledMethod() {
    return myCalledMethod;
  }

  @Override
  @NotNull
  public PsiType getType () {
    return myType;
  }

  @Override
  @NotNull
  public PsiType getDefaultType () {
    return myDefaultType;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof ExpectedTypeInfoImpl)) return false;

    final ExpectedTypeInfoImpl that = (ExpectedTypeInfoImpl)o;

    if (myKind != that.myKind) return false;
    if (!myDefaultType.equals(that.myDefaultType)) return false;
    if (!myTailType.equals(that.myTailType)) return false;
    if (!myType.equals(that.myType)) return false;

    return true;
  }

  public int hashCode() {
    int result = myType.hashCode();
    result = 31 * result + myDefaultType.hashCode();
    result = 31 * result + myKind;
    result = 31 * result + myTailType.hashCode();
    return result;
  }

  @Override
  public boolean equals(ExpectedTypeInfo obj) {
    return equals((Object)obj);
  }

  public String toString() {
    return "ExpectedTypeInfo[type='" + myType + "' kind='" + myKind + "']";
  }

  @Override
  public ExpectedTypeInfo @NotNull [] intersect(@NotNull ExpectedTypeInfo info) {
    ExpectedTypeInfoImpl info1 = (ExpectedTypeInfoImpl)info;

    if (myKind == TYPE_STRICTLY) {
      if (info1.myKind == TYPE_STRICTLY) {
        if (info1.myType.equals(myType)) return new ExpectedTypeInfoImpl[] {this};
      }
      else {
        return info1.intersect(this);
      }
    }
    else if (myKind == TYPE_OR_SUBTYPE) {
      if (info1.myKind == TYPE_STRICTLY) {
        if (myType.isAssignableFrom(info1.myType)) return new ExpectedTypeInfoImpl[] {info1};
      }
      else if (info1.myKind == TYPE_OR_SUBTYPE) {
        PsiType otherType = info1.myType;
        if (myType.isAssignableFrom(otherType)) return new ExpectedTypeInfoImpl[] {info1};
        else if (otherType.isAssignableFrom(myType)) return new ExpectedTypeInfoImpl[] {this};
      }
      else {
        return info1.intersect(this);
      }
    }
    else if (myKind == TYPE_OR_SUPERTYPE) {
      if (info1.myKind == TYPE_STRICTLY) {
        if (info1.myType.isAssignableFrom(myType)) return new ExpectedTypeInfoImpl[] {info1};
      }
      else if (info1.myKind == TYPE_OR_SUBTYPE) {
        if (info1.myType.isAssignableFrom(myType)) return new ExpectedTypeInfoImpl[] {this};
      }
      else if (info1.myKind == TYPE_OR_SUPERTYPE) {
        PsiType otherType = info1.myType;
        if (myType.isAssignableFrom(otherType)) return new ExpectedTypeInfoImpl[] {this};
        else if (otherType.isAssignableFrom(myType)) return new ExpectedTypeInfoImpl[] {info1};
      }
      else {
        return info1.intersect(this);
      }
    }


    //todo: the following cases are not implemented: SUPERxSUB, SUBxSUPER

    return ExpectedTypeInfo.EMPTY_ARRAY;
  }

  @NotNull
  ExpectedTypeInfoImpl fixUnresolvedTypes(@NotNull PsiElement context) {
    PsiType resolvedType = fixUnresolvedType(context, myType);
    PsiType resolvedDefaultType = fixUnresolvedType(context, myDefaultType);
    if (resolvedType != myType || resolvedDefaultType != myDefaultType) {
      return new ExpectedTypeInfoImpl(resolvedType, myKind, resolvedDefaultType, myTailType, myCalledMethod, expectedNameComputable);
    }
    return this;
  }

  @NotNull
  private static PsiType fixUnresolvedType(@NotNull PsiElement context, @NotNull PsiType type) {
    if (type instanceof PsiClassType && ((PsiClassType)type).resolve() == null) {
      String className = ((PsiClassType)type).getClassName();
      int typeParamCount = ((PsiClassType)type).getParameterCount();
      Project project = context.getProject();
      PsiResolveHelper helper = PsiResolveHelper.SERVICE.getInstance(project);
      List<PsiClass> suitableClasses = ContainerUtil.filter(
        PsiShortNamesCache.getInstance(project).getClassesByName(className, context.getResolveScope()),
        c -> (typeParamCount == 0 || c.hasTypeParameters()) &&
             helper.isAccessible(c, context, null) &&
             ImportClassFixBase.qualifiedNameAllowsAutoImport(context.getContainingFile(), c));
      if (suitableClasses.size() == 1) {
        return PsiElementFactory.getInstance(project).createType(suitableClasses.get(0), ((PsiClassType)type).getParameters());
      }
    }
    return type;
  }
}
