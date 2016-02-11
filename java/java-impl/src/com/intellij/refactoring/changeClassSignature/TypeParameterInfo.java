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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * @author dsl
 */
public interface TypeParameterInfo {
  String getName(PsiTypeParameter[] parameters);

  PsiTypeParameter getTypeParameter(PsiTypeParameter[] parameters, Project project);

  class New implements TypeParameterInfo {
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
           JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createTypeFromText(defaultValue, aClass.getLBrace()),
           boundValue.isEmpty()
           ? null
           : JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createTypeFromText(boundValue, aClass.getLBrace()));
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
        return StringUtil.join(ContainerUtil.map(((PsiIntersectionType)boundType).getConjuncts(), new Function<PsiType, String>() {
          @Override
          public String fun(PsiType type) {
            return type.getCanonicalText();
          }
        }), " & ");
      }
      return boundType.getCanonicalText();
    }
  }

  class Existing implements TypeParameterInfo {
    private final int myOldParameterIndex;

    public Existing(int oldIndex) {
      myOldParameterIndex = oldIndex;
    }

    public int getParameterIndex() {
      return myOldParameterIndex;
    }

    @Override
    public String getName(PsiTypeParameter[] parameters) {
      return parameters[myOldParameterIndex].getName();
    }

    @Override
    public PsiTypeParameter getTypeParameter(PsiTypeParameter[] parameters, Project project) {
      return parameters[myOldParameterIndex];
    }
  }
}
