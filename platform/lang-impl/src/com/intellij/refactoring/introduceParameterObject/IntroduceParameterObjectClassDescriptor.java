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
package com.intellij.refactoring.introduceParameterObject;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.changeSignature.ParameterInfo;

public abstract class IntroduceParameterObjectClassDescriptor<M extends PsiNamedElement, P extends ParameterInfo> {
  private final String myClassName;
  private final String myPackageName;
  private final boolean myUseExistingClass;
  private final boolean myCreateInnerClass;
  private final String myNewVisibility;
  private final boolean myGenerateAccessors;
  private final P[] myParamsToMerge;
  private PsiElement myExistingClass;

  public IntroduceParameterObjectClassDescriptor(String className,
                                                 String packageName,
                                                 boolean useExistingClass,
                                                 boolean createInnerClass,
                                                 String newVisibility,
                                                 boolean generateAccessors,
                                                 P[] parameters) {
    myClassName = className;
    myPackageName = packageName;
    myUseExistingClass = useExistingClass;
    myCreateInnerClass = createInnerClass;
    myNewVisibility = newVisibility;
    myGenerateAccessors = generateAccessors;
    myParamsToMerge = parameters;
  }

  public String getClassName() {
    return myClassName;
  }

  public String getPackageName() {
    return myPackageName;
  }

  public boolean isUseExistingClass() {
    return myUseExistingClass;
  }

  public boolean isCreateInnerClass() {
    return myCreateInnerClass;
  }

  public String getNewVisibility() {
    return myNewVisibility;
  }

  public P[] getParamsToMerge() {
    return myParamsToMerge;
  }

  public PsiElement getExistingClass() {
    return myExistingClass;
  }

  public void setExistingClass(PsiElement existingClass) {
    myExistingClass = existingClass;
  }

  public boolean isGenerateAccessors() {
    return myGenerateAccessors;
  }

  public P getParameterInfo(int parameterIdx) {
    for (P info : myParamsToMerge) {
      if (info.getOldIndex() == parameterIdx) {
        return info;
      }
    }
    return null;
  }

  public abstract String getSetterName(P paramInfo, PsiElement context);
  public abstract String getGetterName(P paramInfo, PsiElement context);

  public abstract void initExistingClass(M method);

  public abstract PsiElement createClass(M method, IntroduceParameterObjectDelegate.Accessor[] accessors);
}
