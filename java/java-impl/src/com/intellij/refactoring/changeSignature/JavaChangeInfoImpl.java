/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.refactoring.changeSignature.ChangeSignatureUtil.deepTypeEqual;

/**
 * @author Jeka
 * @since Sep 17, 2001
 */
public class JavaChangeInfoImpl extends UserDataHolderBase implements JavaChangeInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeSignature.JavaChangeInfoImpl");

  @PsiModifier.ModifierConstant final String newVisibility;
  private PsiMethod method;
  String oldName;
  final String oldType;
  String[] oldParameterNames;
  String[] oldParameterTypes;
  final String newName;
  final CanonicalTypes.Type newReturnType;
  final ParameterInfoImpl[] newParms;
  ThrownExceptionInfo[] newExceptions;
  final boolean[] toRemoveParm;
  boolean isVisibilityChanged;
  boolean isNameChanged;
  boolean isReturnTypeChanged;
  boolean isParameterSetOrOrderChanged;
  boolean isExceptionSetChanged;
  boolean isExceptionSetOrOrderChanged;
  boolean isParameterNamesChanged;
  boolean isParameterTypesChanged;
  boolean isPropagationEnabled = true;
  final boolean wasVararg;
  final boolean retainsVarargs;
  final boolean obtainsVarags;
  final boolean arrayToVarargs;
  PsiIdentifier newNameIdentifier;
  final PsiExpression[] defaultValues;

  final boolean isGenerateDelegate;
  final Set<PsiMethod> propagateParametersMethods;
  final Set<PsiMethod> propagateExceptionsMethods;

  private boolean myCheckUnusedParameter = false;

  /**
   * @param newExceptions null if not changed
   */
  public JavaChangeInfoImpl(@PsiModifier.ModifierConstant String newVisibility,
                    PsiMethod method,
                    String newName,
                    CanonicalTypes.Type newType,
                    @NotNull ParameterInfoImpl[] newParms,
                    ThrownExceptionInfo[] newExceptions,
                    boolean generateDelegate,
                    Set<PsiMethod> propagateParametersMethods,
                    Set<PsiMethod> propagateExceptionsMethods) {
    this(newVisibility, method, newName, newType, newParms, newExceptions, generateDelegate, propagateParametersMethods,
         propagateExceptionsMethods, method.getName());
  }

  /**
   * @param newExceptions null if not changed
   * @param oldName
   */
  public JavaChangeInfoImpl(@PsiModifier.ModifierConstant String newVisibility,
                            PsiMethod method,
                            String newName,
                            CanonicalTypes.Type newType,
                            @NotNull ParameterInfoImpl[] newParms,
                            ThrownExceptionInfo[] newExceptions,
                            boolean generateDelegate,
                            Set<PsiMethod> propagateParametersMethods,
                            Set<PsiMethod> propagateExceptionsMethods,
                            String oldName) {
    PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();

    this.newVisibility = newVisibility;
    this.method = method;
    this.newName = newName;
    newReturnType = newType;
    this.newParms = newParms;
    wasVararg = method.isVarArgs();

    this.isGenerateDelegate = generateDelegate;
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
      obtainsVarags = lastNewParm.isVarargType();
      retainsVarargs = lastNewParm.oldParameterIndex >= 0 && obtainsVarags;
      if (retainsVarargs) {
        arrayToVarargs = oldParameterTypes[lastNewParm.oldParameterIndex].endsWith("[]");
      }
      else {
        arrayToVarargs = false;
      }
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

    PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      oldParameterNames[i] = parameter.getName();
      oldParameterTypes[i] = factory.createTypeElement(parameter.getType()).getText();
    }
    if (!method.isConstructor()){
      try {
        isReturnTypeChanged = !deepTypeEqual(newReturnType.getType(this.method), this.method.getReturnType());
      }
      catch (IncorrectOperationException e) {
        isReturnTypeChanged = true;
      }
    }
  }

  @NotNull
  public JavaParameterInfo[] getNewParameters() {
    return newParms;
  }

  @PsiModifier.ModifierConstant
  public String getNewVisibility() {
    return newVisibility;
  }

  public boolean isParameterSetOrOrderChanged() {
    return isParameterSetOrOrderChanged;
  }

  private void setupExceptions(ThrownExceptionInfo[] newExceptions, final PsiMethod method) {
    if (newExceptions == null) {
      newExceptions = JavaThrownExceptionInfo.extractExceptions(method);
    }

    this.newExceptions = newExceptions;

    PsiClassType[] types = method.getThrowsList().getReferencedTypes();
    isExceptionSetChanged = newExceptions.length != types.length;
    if (!isExceptionSetChanged) {
      for (int i = 0; i < newExceptions.length; i++) {
        try {
          if (newExceptions[i].getOldIndex() < 0 || !deepTypeEqual(types[i], newExceptions[i].createType(method, method.getManager()))) {
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

  public PsiMethod getMethod() {
    return method;
  }

  public CanonicalTypes.Type getNewReturnType() {
    return newReturnType;
  }

  public void updateMethod(PsiMethod method) {
    this.method = method;
  }

  @Override
  public Collection<PsiMethod> getMethodsToPropagateParameters() {
    return propagateParametersMethods;
  }

  public ParameterInfoImpl[] getCreatedParmsInfoWithoutVarargs() {
    List<ParameterInfoImpl> result = new ArrayList<>();
    for (ParameterInfoImpl newParm : newParms) {
      if (newParm.oldParameterIndex < 0 && !newParm.isVarargType()) {
        result.add(newParm);
      }
    }
    return result.toArray(new ParameterInfoImpl[result.size()]);
  }

  @Nullable
  public PsiExpression getValue(int i, PsiCallExpression expr) throws IncorrectOperationException {
    if (defaultValues[i] != null) return defaultValues[i];
    final PsiElement valueAtCallSite = newParms[i].getActualValue(expr, PsiSubstitutor.EMPTY);
    return valueAtCallSite instanceof PsiExpression ? (PsiExpression)valueAtCallSite : null;
  }

  public boolean isVisibilityChanged() {
    return isVisibilityChanged;
  }

  public boolean isNameChanged() {
    return isNameChanged;
  }

  public boolean isReturnTypeChanged() {
    return isReturnTypeChanged;
  }

  public String getNewName() {
    return newName;
  }

  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  public boolean isExceptionSetChanged() {
    return isExceptionSetChanged;
  }

  public boolean isExceptionSetOrOrderChanged() {
    return isExceptionSetOrOrderChanged;
  }

  public boolean isParameterNamesChanged() {
    return isParameterNamesChanged;
  }

  public boolean isParameterTypesChanged() {
    return isParameterTypesChanged;
  }

  public boolean isGenerateDelegate() {
    return isGenerateDelegate;
  }

  @NotNull
  public String[] getOldParameterNames() {
    return oldParameterNames;
  }

  @NotNull
  public String[] getOldParameterTypes() {
    return oldParameterTypes;
  }

  public ThrownExceptionInfo[] getNewExceptions() {
    return newExceptions;
  }

  public boolean isRetainsVarargs() {
    return retainsVarargs;
  }

  public boolean isObtainsVarags() {
    return obtainsVarags;
  }

  public boolean isArrayToVarargs() {
    return arrayToVarargs;
  }

  public PsiIdentifier getNewNameIdentifier() {
    return newNameIdentifier;
  }

  public String getOldName() {
    return oldName;
  }

  public boolean wasVararg() {
    return wasVararg;
  }

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
    if (newVisibility != null ? !newVisibility.equals(that.newVisibility) : that.newVisibility != null) return false;
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
    int result = newVisibility != null ? newVisibility.hashCode() : 0;
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
