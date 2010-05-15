/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/**
 * created at Sep 17, 2001
 * @author Jeka
 */
package com.intellij.refactoring.changeSignature;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

class JavaChangeInfoImpl implements JavaChangeInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeSignature.JavaChangeInfoImpl");

  @Modifier
  final String newVisibility;
  private PsiMethod method;
  final String oldName;
  final String oldType;
  final String[] oldParameterNames;
  final String[] oldParameterTypes;
  final String newName;
  final CanonicalTypes.Type newReturnType;
  final ParameterInfoImpl[] newParms;
  ThrownExceptionInfo[] newExceptions;
  final boolean[] toRemoveParm;
  boolean isVisibilityChanged = false;
  boolean isNameChanged = false;
  boolean isReturnTypeChanged = false;
  boolean isParameterSetOrOrderChanged = false;
  boolean isExceptionSetChanged = false;
  boolean isExceptionSetOrOrderChanged = false;
  boolean isParameterNamesChanged = false;
  boolean isParameterTypesChanged = false;
  boolean isPropagationEnabled = true;
  final boolean wasVararg;
  final boolean retainsVarargs;
  final boolean obtainsVarags;
  final boolean arrayToVarargs;
  PsiIdentifier newNameIdentifier;
//  PsiType newTypeElement;
  final PsiExpression[] defaultValues;

  final boolean isGenerateDelegate;
  final Set<PsiMethod> propagateParametersMethods;
  final Set<PsiMethod> propagateExceptionsMethods;

  /**
   * @param newExceptions null if not changed
   */
  public JavaChangeInfoImpl(@Modifier String newVisibility,
                    PsiMethod method,
                    String newName,
                    CanonicalTypes.Type newType,
                    @NotNull ParameterInfoImpl[] newParms,
                    ThrownExceptionInfo[] newExceptions,
                    boolean generateDelegate,
                    Set<PsiMethod> propagateParametersMethods,
                    Set<PsiMethod> propagateExceptionsMethods) {
    this.newVisibility = newVisibility;
    this.method = method;
    this.newName = newName;
    newReturnType = newType;
    this.newParms = newParms;
    wasVararg = method.isVarArgs();

    this.isGenerateDelegate =generateDelegate;
    this.propagateExceptionsMethods=propagateExceptionsMethods;
    this.propagateParametersMethods=propagateParametersMethods;

    oldName = method.getName();
    final PsiManager manager = method.getManager();
    if (!method.isConstructor()){
      oldType = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeElement(method.getReturnType()).getText();
    }
    else{
      oldType = null;
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    oldParameterNames = new String[parameters.length];
    oldParameterTypes = new String[parameters.length];
    for(int i = 0; i < parameters.length; i++){
      PsiParameter parameter = parameters[i];
      oldParameterNames[i] = parameter.getName();
      oldParameterTypes[i] =
        JavaPsiFacade.getInstance(parameter.getProject()).getElementFactory().createTypeElement(parameter.getType()).getText();
    }

    isVisibilityChanged = !method.hasModifierProperty(newVisibility);

    isNameChanged = !newName.equals(oldName);
    if (!method.isConstructor()){
      try {
        isReturnTypeChanged = !newReturnType.getType(this.method, manager).equals(this.method.getReturnType());
      }
      catch (IncorrectOperationException e) {
        isReturnTypeChanged = true;
      }
    }
    if (parameters.length != newParms.length){
      isParameterSetOrOrderChanged = true;
    }
    else {
      for(int i = 0; i < newParms.length; i++){
        ParameterInfoImpl parmInfo = newParms[i];
        PsiParameter parameter = parameters[i];
        if (i != parmInfo.oldParameterIndex){
          isParameterSetOrOrderChanged = true;
          break;
        }
        if (!parmInfo.getName().equals(parameter.getName())){
          isParameterNamesChanged = true;
        }
        try {
          if (!parmInfo.createType(method, manager).equals(parameter.getType())){
            isParameterTypesChanged = true;
          }
        }
        catch (IncorrectOperationException e) {
          isParameterTypesChanged = true;
        }
      }
    }

    setupPropagationEnabled(parameters, newParms);

    setupExceptions(newExceptions, method);

    toRemoveParm = new boolean[parameters.length];
    Arrays.fill(toRemoveParm, true);
    for (ParameterInfoImpl info : newParms) {
      if (info.oldParameterIndex < 0) continue;
      toRemoveParm[info.oldParameterIndex] = false;
    }

    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    defaultValues = new PsiExpression[newParms.length];
    for(int i = 0; i < newParms.length; i++){
      ParameterInfoImpl info = newParms[i];
      if (info.oldParameterIndex < 0 && !info.isVarargType()){
        if (info.defaultValue == null) continue;
        try{
          defaultValues[i] = factory.createExpressionFromText(info.defaultValue, method);
        }
        catch(IncorrectOperationException e){
          LOG.error(e);
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
        final PsiType oldTypeForVararg = parameters[lastNewParm.oldParameterIndex].getType();
        arrayToVarargs = (oldTypeForVararg instanceof PsiArrayType && !(oldTypeForVararg instanceof PsiEllipsisType));
      }
      else {
        arrayToVarargs = false;
      }
    }

    if (isNameChanged) {
      newNameIdentifier = factory.createIdentifier(newName);
    }

  }

  @NotNull
  public JavaParameterInfo[] getNewParameters() {
    return newParms;
  }

  @Modifier
  public String getNewVisibility() {
    return newVisibility;
  }

  public boolean isParameterSetOrOrderChanged() {
    return isParameterSetOrOrderChanged;
  }

  private void setupExceptions(ThrownExceptionInfo[] newExceptions, final PsiMethod method) {
    if (newExceptions == null) newExceptions = extractExceptions(method);

    this.newExceptions = newExceptions;

    PsiClassType[] types = method.getThrowsList().getReferencedTypes();
    isExceptionSetChanged = newExceptions.length != types.length;
    if (!isExceptionSetChanged) {
      for (int i = 0; i < newExceptions.length; i++) {
        try {
          if (newExceptions[i].getOldIndex() < 0 || !types[i].equals(newExceptions[i].createType(method, method.getManager()))) {
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

  private void setupPropagationEnabled(final PsiParameter[] parameters, final ParameterInfoImpl[] newParms) {
    if (parameters.length >= newParms.length) {
      isPropagationEnabled = false;
    }
    else {
      for (int i = 0; i < parameters.length; i++) {
        final ParameterInfoImpl newParm = newParms[i];
        if (newParm.oldParameterIndex != i) {
          isPropagationEnabled = false;
          break;
        }
      }
    }
  }

  //create identity mapping
  private static ThrownExceptionInfo[] extractExceptions(PsiMethod method) {
    PsiClassType[] types = method.getThrowsList().getReferencedTypes();
    ThrownExceptionInfo[] result = new ThrownExceptionInfo[types.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = new JavaThrownExceptionInfo(i, types[i]);
    }
    return result;
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

  public ParameterInfoImpl[] getCreatedParmsInfoWithoutVarargs() {
    List<ParameterInfoImpl> result = new ArrayList<ParameterInfoImpl>();
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
    return newParms[i].getValue(expr);
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
    return StdLanguages.JAVA;
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
}
