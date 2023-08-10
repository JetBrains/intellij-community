// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeClassSignature;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class New implements TypeParameterInfo {
  private String myNewName;
  private CanonicalTypes.Type myDefaultValue;
  private CanonicalTypes.Type myBoundValue;

  public New(@NotNull String name, @Nullable PsiType aType, @Nullable PsiType boundValue) {
    myNewName = name;
    myDefaultValue = aType != null ? CanonicalTypes.createTypeWrapper(aType) : null;
    myBoundValue = boundValue != null ? CanonicalTypes.createTypeWrapper(boundValue) : null;
  }

  @TestOnly
  public New(@NotNull PsiClass aClass,
             @NotNull @NonNls String name,
             @NotNull @NonNls String defaultValue,
             @NotNull @NonNls String boundValue) throws IncorrectOperationException {
    this(name,
         JavaPsiFacade.getElementFactory(aClass.getProject()).createTypeFromText(defaultValue, aClass.getLBrace()),
         boundValue.isEmpty()
         ? null
         : JavaPsiFacade.getElementFactory(aClass.getProject()).createTypeFromText(boundValue, aClass.getLBrace()));
  }

  public void setNewName(String newName) {
    myNewName = newName;
  }

  public void setBoundValue(PsiType aType) {
    myBoundValue = CanonicalTypes.createTypeWrapper(aType);
  }

  public void setDefaultValue(PsiType aType) {
    myDefaultValue = CanonicalTypes.createTypeWrapper(aType);
  }

  @Override
  public String getName(PsiTypeParameter[] parameters) {
    return myNewName;
  }

  @Override
  public PsiTypeParameter getTypeParameter(PsiTypeParameter[] parameters, Project project) {
    final String extendsText = myBoundValue == null
                               ? ""
                               : " extends " + getCanonicalText(myBoundValue.getType(null, PsiManager.getInstance(project)));
    return JavaPsiFacade.getElementFactory(project).createTypeParameterFromText(myNewName +
                                                                                extendsText, null);
  }

  public CanonicalTypes.Type getDefaultValue() {
    return myDefaultValue;
  }

  private static String getCanonicalText(PsiType boundType) {
    if (boundType instanceof PsiIntersectionType) {
      return StringUtil.join(ContainerUtil.map(((PsiIntersectionType)boundType).getConjuncts(), type -> type.getCanonicalText()), " & ");
    }
    return boundType.getCanonicalText();
  }
}
