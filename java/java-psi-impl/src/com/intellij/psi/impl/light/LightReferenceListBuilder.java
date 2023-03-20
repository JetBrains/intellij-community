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
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class LightReferenceListBuilder extends LightElement implements PsiReferenceList {
  private final List<PsiJavaCodeReferenceElement> myRefs = new ArrayList<>();
  private volatile PsiClassType[] myCachedTypes;
  private final Role myRole;
  private final PsiElementFactory myFactory;

  public LightReferenceListBuilder(PsiManager manager, Role role) {
    this(manager, JavaLanguage.INSTANCE, role);
  }

  public LightReferenceListBuilder(PsiManager manager, Language language, Role role) {
    super(manager, language);
    myRole = role;
    myFactory = JavaPsiFacade.getElementFactory(getProject());
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
  @Override
  public String toString() {
    return "light reference list";
  }

  public void addReference(PsiClass aClass) {
    addReference(aClass.getQualifiedName());
  }

  public void addReference(String qualifiedName) {
    myRefs.add(myFactory.createReferenceElementByFQClassName(qualifiedName, getResolveScope()));
  }

  public void addReference(PsiClassType type) {
    myRefs.add(myFactory.createReferenceElementByType(type));
  }

  @Override
  public PsiJavaCodeReferenceElement @NotNull [] getReferenceElements() {
    return myRefs.toArray(PsiJavaCodeReferenceElement.EMPTY_ARRAY);
  }

  @Override
  public PsiClassType @NotNull [] getReferencedTypes() {
    PsiClassType[] types = myCachedTypes;
    if (types == null) {
      int size = myRefs.size();
      types = size == 0 ? PsiClassType.EMPTY_ARRAY : new PsiClassType[size];
      for (int i = 0; i < size; i++) {
        types[i] = myFactory.createType(myRefs.get(i));
      }
      myCachedTypes = types;
    }

    return types;
  }

  @Override
  public Role getRole() {
    return myRole;
  }
}
