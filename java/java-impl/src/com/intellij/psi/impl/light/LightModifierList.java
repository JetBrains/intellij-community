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
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public class LightModifierList extends LightElement implements PsiModifierList{
  private final Set<String> myModifiers;

  public LightModifierList(PsiManager manager){
    this(manager, StdLanguages.JAVA);
  }

  public LightModifierList(PsiManager manager, final Language language, String... modifiers){
    super(manager, language);
    myModifiers = Collections.synchronizedSet(CollectionFactory.newTroveSet(modifiers));
  }

  public void addModifier(String modifier) {
    myModifiers.add(modifier);
  }

  public void clearModifiers() {
    myModifiers.clear();
  }

  public boolean hasModifierProperty(@NotNull String name){
    return myModifiers.contains(name);
  }

  public boolean hasExplicitModifier(@NotNull String name) {
    return myModifiers.contains(name);
  }

  public void setModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public void checkSetModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    //todo
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    return getAnnotations();
  }

  public PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    return null;
  }

  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    throw new UnsupportedOperationException("Method addAnnotation is not yet implemented in " + getClass().getName());
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitModifierList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiModifierList";
  }

}
