/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.changeClassSignature;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

/**
 * @author dsl
 */
public class TypeParameterInfo {
  private final int myOldParameterIndex;
  private String myNewName;
  private CanonicalTypes.Type myDefaultValue;

  public TypeParameterInfo(int oldIndex) {
    myOldParameterIndex = oldIndex;
    myDefaultValue = null;
  }

  public TypeParameterInfo(String name, PsiType aType) {
    myOldParameterIndex = -1;
    myNewName = name;
    if (aType != null) {
      myDefaultValue = CanonicalTypes.createTypeWrapper(aType);
    }
    else {
      myDefaultValue = null;
    }
  }

  TypeParameterInfo(PsiClass aClass, @NonNls String name, @NonNls String defaultValue) throws IncorrectOperationException {
    this(name, JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createTypeFromText(defaultValue, aClass.getLBrace()));
  }


  public int getOldParameterIndex() {
    return myOldParameterIndex;
  }

  public String getNewName() {
    return myNewName;
  }

  public void setNewName(String newName) {
    myNewName = newName;
  }

  public CanonicalTypes.Type getDefaultValue() {
    return myDefaultValue;
  }

  public void setDefaultValue(CanonicalTypes.Type defaultValue) {
    myDefaultValue = defaultValue;
  }

  public void setDefaultValue(PsiType aType) {
    setDefaultValue(CanonicalTypes.createTypeWrapper(aType));
  }

  boolean isForExistingParameter() {
    return myOldParameterIndex >= 0;
  }
}
