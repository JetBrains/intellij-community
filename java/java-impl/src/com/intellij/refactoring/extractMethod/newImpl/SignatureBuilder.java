// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;

import java.util.Collections;
import java.util.List;

public class SignatureBuilder {

  private final PsiElementFactory factory;
  @PsiModifier.ModifierConstant private String visibility = PsiModifier.PRIVATE;
  private boolean isStatic = false;
  private PsiType returnType = PsiType.VOID;
  private String methodName = "extracted";
  private List<String> parameterNames = Collections.emptyList();
  private List<PsiType> parameterTypes = Collections.emptyList();
  private List<PsiClassType> thrownExceptions = Collections.emptyList();

  public SignatureBuilder(Project project) {
    this.factory = PsiElementFactory.getInstance(project);
  }

  public SignatureBuilder visibility(@PsiModifier.ModifierConstant String visibility){
    this.visibility = visibility;
    return this;
  }

  public SignatureBuilder makeStatic(boolean isStatic) {
    this.isStatic = isStatic;
    return this;
  }

  public SignatureBuilder throwExceptions(List<PsiClassType> exceptions){
    this.thrownExceptions = exceptions;
    return this;
  }

  public SignatureBuilder methodName(String methodName) {
    this.methodName = methodName;
    return this;
  }

  public SignatureBuilder parameterNames(List<String> parameterNames) {
    this.parameterNames = parameterNames;
    return this;
  }

  public SignatureBuilder parameterTypes(List<PsiType> parameterTypes) {
    this.parameterTypes = parameterTypes;
    return this;
  }

  public SignatureBuilder returnType(PsiType returnType){
    this.returnType = returnType;
    return this;
  }

  public List<String> parameterNames(){
    return parameterNames;
  }

  public List<PsiType> parameterTypes(){
    return parameterTypes;
  }

  public PsiMethod build() {
    if (parameterNames.size() != parameterTypes.size()) throw new IllegalStateException("Different number of parameter names and types");

    final String[] names = ArrayUtil.toStringArray(parameterNames);
    final PsiType[] types = parameterTypes.toArray(PsiType.EMPTY_ARRAY);
    final PsiParameterList parameterList = factory.createParameterList(names, types);

    final PsiMethod method = factory.createMethod(methodName, returnType);
    method.getParameterList().replace(parameterList);
    method.getModifierList().setModifierProperty(PsiModifier.STATIC, isStatic);
    method.getModifierList().setModifierProperty(visibility, true);
    thrownExceptions.forEach(exception -> method.getThrowsList().add(factory.createReferenceElementByType(exception)));

    return method;
  }
}
