/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.psi.impl.source.PsiModifierListImpl;
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
    return hasMaskModifierProperty(name, getStub().getModifiersMask());
  }

  public static boolean hasMaskModifierProperty(String name, int mask) {
    int flag = PsiModifierListImpl.NAME_TO_MODIFIER_FLAG_MAP.get(name);
    assert flag != 0;
    return (mask & flag) != 0;
  }

  @Override
  public boolean hasExplicitModifier(@NotNull String name) {
    return hasModifierProperty(name);
  }

  @Override
  public void setModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  @Override
  public void checkSetModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
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
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    final PsiElement parent = getParent();
    final PsiAnnotation[] annotations = getAnnotations();
    final boolean separateAnnotations = parent instanceof PsiClass || parent instanceof PsiMethod || parent instanceof PsiField;

    for (int i = 0; i < annotations.length; i++) {
      appendText(annotations[i], indentLevel, buffer, separateAnnotations ? NEXT_LINE : " ");
    }

    //TODO : filtering & ordering modifiers can go to CodeStyleManager
    final boolean isClass = parent instanceof PsiClass;
    final boolean isInterface = isClass && ((PsiClass)parent).isInterface();
    final boolean isInterfaceClass = isClass && parent.getParent() instanceof PsiClass && ((PsiClass)parent.getParent()).isInterface();
    final boolean isMethod = parent instanceof PsiMethod;
    final boolean isInterfaceMethod = isMethod && parent.getParent() instanceof PsiClass && ((PsiClass)parent.getParent()).isInterface();
    final boolean isField = parent instanceof PsiField;
    final boolean isInterfaceField = isField && parent.getParent() instanceof PsiClass && ((PsiClass)parent.getParent()).isInterface();

    if (hasModifierProperty(PsiModifier.PUBLIC) && !isInterfaceMethod && !isInterfaceField && !isInterfaceClass) {
      buffer.append(PsiModifier.PUBLIC);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.PROTECTED)) {
      buffer.append(PsiModifier.PROTECTED);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.PRIVATE)) {
      buffer.append(PsiModifier.PRIVATE);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.STATIC) && !isInterfaceField) {
      buffer.append(PsiModifier.STATIC);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.ABSTRACT) && !isInterface && !isInterfaceMethod) {
      buffer.append(PsiModifier.ABSTRACT);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.FINAL) && !isInterfaceField) {
      buffer.append(PsiModifier.FINAL);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.NATIVE)) {
      buffer.append(PsiModifier.NATIVE);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
      buffer.append(PsiModifier.SYNCHRONIZED);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.TRANSIENT)) {
      buffer.append(PsiModifier.TRANSIENT);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.VOLATILE)) {
      buffer.append(PsiModifier.VOLATILE);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.STRICTFP)) {
      buffer.append(PsiModifier.STRICTFP);
      buffer.append(' ');
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
