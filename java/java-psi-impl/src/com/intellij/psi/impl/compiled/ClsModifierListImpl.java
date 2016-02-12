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
package com.intellij.psi.impl.compiled;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("ForLoopReplaceableByForEach")
public class ClsModifierListImpl extends ClsRepositoryPsiElement<PsiModifierListStub> implements PsiModifierList {
  public ClsModifierListImpl(final PsiModifierListStub stub) {
    super(stub);
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    return getAnnotations();
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return ModifierFlags.hasModifierProperty(name, getStub().getModifiersMask());
  }

  @Override
  public boolean hasExplicitModifier(@NotNull String name) {
    return hasModifierProperty(name);
  }

  @Override
  public void setModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public void checkSetModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  @NotNull
  public PsiAnnotation[] getAnnotations() {
    return getStub().getChildrenByType(JavaStubElementTypes.ANNOTATION, PsiAnnotation.ARRAY_FACTORY);
  }

  @Override
  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    return getAnnotations();
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  @Override
  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    throw cannotModifyException(this);
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    final PsiElement parent = getParent();
    final PsiAnnotation[] annotations = getAnnotations();
    final boolean separateAnnotations = parent instanceof PsiClass || parent instanceof PsiMethod || parent instanceof PsiField;

    for (int i = 0; i < annotations.length; i++) {
      appendText(annotations[i], indentLevel, buffer, separateAnnotations ? NEXT_LINE : " ");
    }

    final boolean isClass = parent instanceof PsiClass;
    final boolean isInterface = isClass && ((PsiClass)parent).isInterface();
    final boolean isEnum = isClass && ((PsiClass)parent).isEnum();
    final boolean isInterfaceClass = isClass && parent.getParent() instanceof PsiClass && ((PsiClass)parent.getParent()).isInterface();
    final boolean isMethod = parent instanceof PsiMethod;
    final boolean isInterfaceMethod = isMethod && parent.getParent() instanceof PsiClass && ((PsiClass)parent.getParent()).isInterface();
    final boolean isField = parent instanceof PsiField;
    final boolean isInterfaceField = isField && parent.getParent() instanceof PsiClass && ((PsiClass)parent.getParent()).isInterface();
    final boolean isEnumConstant = parent instanceof PsiEnumConstant;

    if (hasModifierProperty(PsiModifier.PUBLIC) && !isInterfaceMethod && !isInterfaceField && !isInterfaceClass && !isEnumConstant) {
      buffer.append(PsiModifier.PUBLIC).append(' ');
    }
    if (hasModifierProperty(PsiModifier.PROTECTED)) {
      buffer.append(PsiModifier.PROTECTED).append(' ');
    }
    if (hasModifierProperty(PsiModifier.PRIVATE)) {
      buffer.append(PsiModifier.PRIVATE).append(' ');
    }
    if (hasModifierProperty(PsiModifier.STATIC) && !isInterfaceField && !isEnumConstant) {
      buffer.append(PsiModifier.STATIC).append(' ');
    }
    if (hasModifierProperty(PsiModifier.ABSTRACT) && !isInterface && !isInterfaceMethod) {
      buffer.append(PsiModifier.ABSTRACT).append(' ');
    }
    if (hasModifierProperty(PsiModifier.FINAL) && !isEnum && !isInterfaceField && !isEnumConstant) {
      buffer.append(PsiModifier.FINAL).append(' ');
    }
    if (hasModifierProperty(PsiModifier.NATIVE)) {
      buffer.append(PsiModifier.NATIVE).append(' ');
    }
    if (hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
      buffer.append(PsiModifier.SYNCHRONIZED).append(' ');
    }
    if (hasModifierProperty(PsiModifier.TRANSIENT)) {
      buffer.append(PsiModifier.TRANSIENT).append(' ');
    }
    if (hasModifierProperty(PsiModifier.VOLATILE)) {
      buffer.append(PsiModifier.VOLATILE).append(' ');
    }
    if (hasModifierProperty(PsiModifier.STRICTFP)) {
      buffer.append(PsiModifier.STRICTFP).append(' ');
    }
    if (hasModifierProperty(PsiModifier.DEFAULT)) {
      buffer.append(PsiModifier.DEFAULT).append(' ');
    }
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.MODIFIER_LIST);
    setMirrors(getAnnotations(), SourceTreeToPsiMap.<PsiModifierList>treeToPsiNotNull(element).getAnnotations());
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitModifierList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiModifierList";
  }
}
