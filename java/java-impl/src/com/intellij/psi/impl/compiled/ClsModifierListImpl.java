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
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
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

public class ClsModifierListImpl extends ClsRepositoryPsiElement<PsiModifierListStub> implements PsiModifierList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsModifierListImpl");

  public ClsModifierListImpl(final PsiModifierListStub stub) {
    super(stub);
  }

  @NotNull
  public PsiElement[] getChildren() {
    return getAnnotations();
  }

  public boolean hasModifierProperty(@NotNull String name) {
    int flag = PsiModifierListImpl.NAME_TO_MODIFIER_FLAG_MAP.get(name);
    assert flag != 0;
    return (getStub().getModifiersMask() & flag) != 0;
  }

  public boolean hasExplicitModifier(@NotNull String name) {
    return hasModifierProperty(name);
  }

  public void setModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public void checkSetModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    return getStub().getChildrenByType(JavaStubElementTypes.ANNOTATION, PsiAnnotation.ARRAY_FACTORY);
  }

  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    return getAnnotations();
  }

  public PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  private boolean isAnnotationFormattingAllowed() {
    final PsiElement element = getParent();
    return element instanceof PsiClass
        || element instanceof PsiMethod
        || element instanceof PsiField;
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    final PsiAnnotation[] annotations = getAnnotations();
    final boolean formattingAllowed = isAnnotationFormattingAllowed();
    for (PsiAnnotation annotation : annotations) {
      ((ClsAnnotationImpl)annotation).appendMirrorText(indentLevel, buffer);
      if (formattingAllowed) {
        goNextLine(indentLevel, buffer);
      }
      else {
        buffer.append(' ');
      }
    }

    final PsiElement parent = getParent();

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

  public void setMirror(@NotNull TreeElement element) {
    setMirrorCheckingType(element, JavaElementType.MODIFIER_LIST);

    PsiElement[] mirrorAnnotations = SourceTreeToPsiMap.<PsiModifierList>treeToPsiNotNull(element).getAnnotations();
    PsiAnnotation[] annotations = getAnnotations();
    LOG.assertTrue(annotations.length == mirrorAnnotations.length);
    for (int i = 0; i < annotations.length; i++) {
      ((ClsElementImpl)annotations[i]).setMirror(SourceTreeToPsiMap.psiToTreeNotNull(mirrorAnnotations[i]));
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitModifierList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiModifierList";
  }
}
