// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Jeka
 */
public class JavaChangeInfoImpl extends UserDataHolderBase implements JavaChangeInfo {
  private static final Logger LOG = Logger.getInstance(JavaChangeInfoImpl.class);

  @PsiModifier.ModifierConstant
  @NotNull
  private final String newVisibility;
  boolean propagateVisibility;
  
  @NotNull
  private PsiMethod method;
  @NotNull
  private final String oldName;
  private final String oldType;
  String[] oldParameterNames;
  String[] oldParameterTypes;
  @NotNull
  private final String newName;
  final CanonicalTypes.Type newReturnType;
  final ParameterInfoImpl[] newParms;
  private ThrownExceptionInfo[] newExceptions;
  private final boolean[] toRemoveParm;
  private final boolean isVisibilityChanged;
  private final boolean isNameChanged;
  boolean isReturnTypeChanged;
  private boolean isParameterSetOrOrderChanged;
  private boolean isExceptionSetChanged;
  private boolean isExceptionSetOrOrderChanged;
  private boolean isParameterNamesChanged;
  private boolean isParameterTypesChanged;
  boolean isPropagationEnabled = true;
  private final boolean wasVararg;
  private final boolean retainsVarargs;
  private final boolean obtainsVarags;
  private final boolean arrayToVarargs;
  private PsiIdentifier newNameIdentifier;
  private final PsiExpression[] defaultValues;

  private final boolean isGenerateDelegate;
  final Set<PsiMethod> propagateParametersMethods;
  final Set<PsiMethod> propagateExceptionsMethods;

  private boolean myCheckUnusedParameter;

  /**
   * @param newExceptions null if not changed
   */
  public JavaChangeInfoImpl(@PsiModifier.ModifierConstant @NotNull String newVisibility,
                            @NotNull PsiMethod method,
                            @NotNull String newName,
                            CanonicalTypes.Type newType,
                            ParameterInfoImpl @NotNull [] newParms,
                            ThrownExceptionInfo @Nullable [] newExceptions,
                            boolean generateDelegate,
                            @NotNull Set<PsiMethod> propagateParametersMethods,
                            @NotNull Set<PsiMethod> propagateExceptionsMethods) {
    this(newVisibility, method, newName, newType, newParms, newExceptions, generateDelegate, propagateParametersMethods,
         propagateExceptionsMethods, method.getName());
  }

  /**
   * @param newExceptions null if not changed
   */
  public JavaChangeInfoImpl(@PsiModifier.ModifierConstant @NotNull String newVisibility,
                            @NotNull PsiMethod method,
                            @NotNull String newName,
                            CanonicalTypes.Type newType,
                            ParameterInfoImpl @NotNull [] newParms,
                            ThrownExceptionInfo @Nullable [] newExceptions,
                            boolean generateDelegate,
                            @NotNull Set<PsiMethod> propagateParametersMethods,
                            @NotNull Set<PsiMethod> propagateExceptionsMethods,
                            @NotNull String oldName) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());

    this.newVisibility = newVisibility;
    this.method = method;
    this.newName = newName;
    newReturnType = newType;
    this.newParms = newParms;
    wasVararg = method.isVarArgs();

    isGenerateDelegate = generateDelegate;
    this.propagateExceptionsMethods = propagateExceptionsMethods;
    this.propagateParametersMethods = propagateParametersMethods;

    this.oldName = oldName;

    if (!method.isConstructor()){
      PsiType type = method.getReturnType();
      assert type != null : method;
      oldType = factory.createTypeElement(type).getText();
    }
    else{
      oldType = null;
    }

    fillOldParams(method);

    isVisibilityChanged = !method.hasModifierProperty(newVisibility);

    isNameChanged = !newName.equals(this.oldName);

    if (oldParameterNames.length != newParms.length){
      isParameterSetOrOrderChanged = true;
    }
    else {
      for(int i = 0; i < newParms.length; i++){
        ParameterInfoImpl parmInfo = newParms[i];

        if (i != parmInfo.oldParameterIndex){
          isParameterSetOrOrderChanged = true;
          break;
        }
        if (!parmInfo.getName().equals(oldParameterNames[i])){
          isParameterNamesChanged = true;
        }
        try {
          if (!parmInfo.getTypeText().equals(oldParameterTypes[i])){
            isParameterTypesChanged = true;
          }
        }
        catch (IncorrectOperationException e) {
          isParameterTypesChanged = true;
        }
      }
    }

    setupPropagationEnabled(method.getParameterList().getParameters(), newParms);

    setupExceptions(newExceptions, method);

    toRemoveParm = new boolean[oldParameterNames.length];
    Arrays.fill(toRemoveParm, true);
    for (ParameterInfoImpl info : newParms) {
      if (info.oldParameterIndex < 0) continue;
      toRemoveParm[info.oldParameterIndex] = false;
    }

    defaultValues = new PsiExpression[newParms.length];
    for(int i = 0; i < newParms.length; i++){
      ParameterInfoImpl info = newParms[i];
      if (info.oldParameterIndex < 0 && !info.isVarargType()){
        if (StringUtil.isEmpty(info.defaultValue)) continue;
        try{
          defaultValues[i] = factory.createExpressionFromText(info.defaultValue, method);
        }
        catch(IncorrectOperationException e){
          LOG.info(e);
        }
      }
    }

    if (this.newParms.length == 0) {
      retainsVarargs = false;
      obtainsVarags = false;
      arrayToVarargs = false;
    }
    else {
      final ParameterInfoImpl lastNewParm = this.newParms[this.newParms.length - 1];
      boolean isVarargs = lastNewParm.isVarargType();
      obtainsVarags = isVarargs && lastNewParm.oldParameterIndex < 0;
      retainsVarargs = lastNewParm.oldParameterIndex >= 0 && oldParameterTypes[lastNewParm.oldParameterIndex].endsWith("...") && isVarargs;
      arrayToVarargs = lastNewParm.oldParameterIndex >= 0 && oldParameterTypes[lastNewParm.oldParameterIndex].endsWith("[]") && isVarargs;
    }

    if (isNameChanged) {
      newNameIdentifier = factory.createIdentifier(newName);
    }
  }

  @Override
  public boolean checkUnusedParameter() {
    return myCheckUnusedParameter;
  }

  public void setCheckUnusedParameter() {
    myCheckUnusedParameter = true;
  }

  protected void fillOldParams(PsiMethod method) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    oldParameterNames = new String[parameters.length];
    oldParameterTypes = new String[parameters.length];

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      oldParameterNames[i] = parameter.getName();
      oldParameterTypes[i] = factory.createTypeElement(parameter.getType()).getText();
    }
    if (!method.isConstructor()){
      try {
        isReturnTypeChanged = !CommonJavaRefactoringUtil.deepTypeEqual(newReturnType.getType(this.method), this.method.getReturnType());
      }
      catch (IncorrectOperationException e) {
        isReturnTypeChanged = true;
      }
    }
  }

  @Override
  public JavaParameterInfo @NotNull [] getNewParameters() {
    return newParms;
  }

  @NotNull
  @Override
  @PsiModifier.ModifierConstant
  public String getNewVisibility() {
    return newVisibility;
  }

  @Override
  public boolean isParameterSetOrOrderChanged() {
    return isParameterSetOrOrderChanged;
  }

  private void setupExceptions(ThrownExceptionInfo @Nullable [] newExceptions, @NotNull PsiMethod method) {
    if (newExceptions == null) {
      newExceptions = JavaThrownExceptionInfo.extractExceptions(method);
    }

    this.newExceptions = newExceptions;

    PsiClassType[] types = method.getThrowsList().getReferencedTypes();
    isExceptionSetChanged = newExceptions.length != types.length;
    if (!isExceptionSetChanged) {
      for (int i = 0; i < newExceptions.length; i++) {
        try {
          if (newExceptions[i].getOldIndex() < 0 || !CommonJavaRefactoringUtil.deepTypeEqual(types[i], newExceptions[i].createType(method, method.getManager()))) {
            isExceptionSetChanged = true;
            break;
          }
        }
        catch (IncorrectOperationException e) {
          isExceptionSetChanged = true;
        }
        if (newExceptions[i].getOldIndex() != i) isExceptionSetOrOrderChanged = true;
      }
    }

    isExceptionSetOrOrderChanged |= isExceptionSetChanged;
  }

  protected void setupPropagationEnabled(final PsiParameter[] parameters, final ParameterInfoImpl[] newParms) {
    if (parameters.length >= newParms.length) {
      isPropagationEnabled = false;
    }
    else {
      isPropagationEnabled = !propagateParametersMethods.isEmpty();
      for (int i = 0; i < parameters.length; i++) {
        final ParameterInfoImpl newParm = newParms[i];
        if (newParm.oldParameterIndex != i) {
          isPropagationEnabled = false;
          break;
        }
      }
    }
  }

  @Override
  public @NotNull PsiMethod getMethod() {
    return method;
  }

  @Override
  public CanonicalTypes.Type getNewReturnType() {
    return newReturnType;
  }

  @Override
  public void updateMethod(@NotNull PsiMethod method) {
    this.method = method;
  }

  @Override
  public @NotNull Collection<PsiMethod> getMethodsToPropagateParameters() {
    return propagateParametersMethods;
  }

  public ParameterInfoImpl @NotNull [] getCreatedParmsInfoWithoutVarargs() {
    List<ParameterInfoImpl> result = new ArrayList<>();
    for (ParameterInfoImpl newParm : newParms) {
      if (newParm.oldParameterIndex < 0 && !newParm.isVarargType()) {
        result.add(newParm);
      }
    }
    return result.toArray(new ParameterInfoImpl[0]);
  }

  @Override
  @Nullable
  public PsiExpression getValue(int i, PsiCallExpression expr) throws IncorrectOperationException {
    if (defaultValues[i] != null) return defaultValues[i];
    final PsiElement valueAtCallSite = newParms[i].getActualValue(expr, PsiSubstitutor.EMPTY);
    return valueAtCallSite instanceof PsiExpression ? (PsiExpression)valueAtCallSite : null;
  }

  @Override
  public boolean isVisibilityChanged() {
    return isVisibilityChanged;
  }

  @Override
  public boolean isNameChanged() {
    return isNameChanged;
  }

  @Override
  public boolean isReturnTypeChanged() {
    return isReturnTypeChanged;
  }

  @Override
  @NotNull
  public String getNewName() {
    return newName;
  }

  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  public boolean isExceptionSetChanged() {
    return isExceptionSetChanged;
  }

  @Override
  public boolean isExceptionSetOrOrderChanged() {
    return isExceptionSetOrOrderChanged;
  }

  @Override
  public boolean isParameterNamesChanged() {
    return isParameterNamesChanged;
  }

  @Override
  public boolean isParameterTypesChanged() {
    return isParameterTypesChanged;
  }

  @Override
  public boolean isGenerateDelegate() {
    return isGenerateDelegate;
  }

  @Override
  public String @NotNull [] getOldParameterNames() {
    return oldParameterNames;
  }

  @Override
  public String @NotNull [] getOldParameterTypes() {
    return oldParameterTypes;
  }

  @Override
  public @Nullable String getOldReturnType() {
    return oldType;
  }

  @Override
  public ThrownExceptionInfo[] getNewExceptions() {
    return newExceptions;
  }

  @Override
  public boolean isRetainsVarargs() {
    return retainsVarargs;
  }

  @Override
  public boolean isObtainsVarags() {
    return obtainsVarags;
  }

  @Override
  public boolean isArrayToVarargs() {
    return arrayToVarargs;
  }

  @Override
  public PsiIdentifier getNewNameIdentifier() {
    return newNameIdentifier;
  }

  @Override
  @NotNull
  public String getOldName() {
    return oldName;
  }

  @Override
  public boolean wasVararg() {
    return wasVararg;
  }

  @Override
  public boolean[] toRemoveParm() {
    return toRemoveParm;
  }

  protected boolean checkMethodEquality() {
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JavaChangeInfoImpl)) return false;

    JavaChangeInfoImpl that = (JavaChangeInfoImpl)o;

    if (arrayToVarargs != that.arrayToVarargs) return false;
    if (isExceptionSetChanged != that.isExceptionSetChanged) return false;
    if (isExceptionSetOrOrderChanged != that.isExceptionSetOrOrderChanged) return false;
    if (isGenerateDelegate != that.isGenerateDelegate) return false;
    if (isNameChanged != that.isNameChanged) return false;
    if (isParameterNamesChanged != that.isParameterNamesChanged) return false;
    if (isParameterSetOrOrderChanged != that.isParameterSetOrOrderChanged) return false;
    if (isParameterTypesChanged != that.isParameterTypesChanged) return false;
    if (isPropagationEnabled != that.isPropagationEnabled) return false;
    if (isReturnTypeChanged != that.isReturnTypeChanged) return false;
    if (isVisibilityChanged != that.isVisibilityChanged) return false;
    if (obtainsVarags != that.obtainsVarags) return false;
    if (retainsVarargs != that.retainsVarargs) return false;
    if (wasVararg != that.wasVararg) return false;
    if (!Arrays.equals(defaultValues, that.defaultValues)) return false;
    if (checkMethodEquality() && !method.equals(that.method)) return false;
    if (!Arrays.equals(newExceptions, that.newExceptions)) return false;
    if (!newName.equals(that.newName)) return false;
    if (newNameIdentifier != null ? !newNameIdentifier.equals(that.newNameIdentifier) : that.newNameIdentifier != null) return false;
    if (!Arrays.equals(newParms, that.newParms)) return false;
    if (newReturnType != null
        ? that.newReturnType == null || !Comparing.strEqual(newReturnType.getTypeText(), that.newReturnType.getTypeText())
        : that.newReturnType != null) {
      return false;
    }
    if (!newVisibility.equals(that.newVisibility)) return false;
    if (!oldName.equals(that.oldName)) return false;
    if (!Arrays.equals(oldParameterNames, that.oldParameterNames)) return false;
    if (!Arrays.equals(oldParameterTypes, that.oldParameterTypes)) return false;
    if (oldType != null ? !oldType.equals(that.oldType) : that.oldType != null) return false;
    if (!propagateExceptionsMethods.equals(that.propagateExceptionsMethods)) return false;
    if (!propagateParametersMethods.equals(that.propagateParametersMethods)) return false;
    if (!Arrays.equals(toRemoveParm, that.toRemoveParm)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = newVisibility.hashCode();
    if (checkMethodEquality()) {
      result = 31 * result + method.hashCode();
    }
    result = 31 * result + oldName.hashCode();
    result = 31 * result + (oldType != null ? oldType.hashCode() : 0);
    result = 31 * result + Arrays.hashCode(oldParameterNames);
    result = 31 * result + Arrays.hashCode(oldParameterTypes);
    result = 31 * result + newName.hashCode();
    result = 31 * result + (newReturnType != null ? newReturnType.getTypeText().hashCode() : 0);
    result = 31 * result + Arrays.hashCode(newParms);
    result = 31 * result + Arrays.hashCode(newExceptions);
    result = 31 * result + Arrays.hashCode(toRemoveParm);
    result = 31 * result + (isVisibilityChanged ? 1 : 0);
    result = 31 * result + (isNameChanged ? 1 : 0);
    result = 31 * result + (isReturnTypeChanged ? 1 : 0);
    result = 31 * result + (isParameterSetOrOrderChanged ? 1 : 0);
    result = 31 * result + (isExceptionSetChanged ? 1 : 0);
    result = 31 * result + (isExceptionSetOrOrderChanged ? 1 : 0);
    result = 31 * result + (isParameterNamesChanged ? 1 : 0);
    result = 31 * result + (isParameterTypesChanged ? 1 : 0);
    result = 31 * result + (isPropagationEnabled ? 1 : 0);
    result = 31 * result + (wasVararg ? 1 : 0);
    result = 31 * result + (retainsVarargs ? 1 : 0);
    result = 31 * result + (obtainsVarags ? 1 : 0);
    result = 31 * result + (arrayToVarargs ? 1 : 0);
    result = 31 * result + (newNameIdentifier != null ? newNameIdentifier.hashCode() : 0);
    result = 31 * result + (defaultValues != null ? Arrays.hashCode(defaultValues) : 0);
    result = 31 * result + (isGenerateDelegate ? 1 : 0);
    result = 31 * result + propagateParametersMethods.hashCode();
    result = 31 * result + propagateExceptionsMethods.hashCode();
    return result;
  }
}
