// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public final class ClsReferenceListImpl extends ClsRepositoryPsiElement<PsiClassReferenceListStub> implements PsiReferenceList {
  private final NotNullLazyValue<PsiJavaCodeReferenceElement[]> myRefs;

  public ClsReferenceListImpl(@NotNull PsiClassReferenceListStub stub) {
    super(stub);
    myRefs = NotNullLazyValue.atomicLazy(() -> {
      TypeInfo[] types = getStub().getTypes();
      if (types.length <= 0) {
        return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
      }
      return ContainerUtil.map2Array(types, PsiJavaCodeReferenceElement.class, info ->
        new ClsJavaCodeReferenceElementImpl(this, info.text, info.getTypeAnnotations()));
    });
  }

  @Override
  public PsiJavaCodeReferenceElement @NotNull [] getReferenceElements() {
    return myRefs.getValue();
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return getReferenceElements();
  }

  @Override
  public PsiClassType @NotNull [] getReferencedTypes() {
    return getStub().getReferencedTypes();
  }

  @Override
  public Role getRole() {
    return getStub().getRole();
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    PsiClassType @NotNull [] types = getStub().getReferencedTypes();
    if (types.length != 0) {
      Role role = getRole();
      switch (role) {
        case EXTENDS_BOUNDS_LIST:
          buffer.append(' ').append(PsiKeyword.EXTENDS).append(' ');
          break;
        case EXTENDS_LIST:
          buffer.append(PsiKeyword.EXTENDS).append(' ');
          break;
        case IMPLEMENTS_LIST:
          buffer.append(PsiKeyword.IMPLEMENTS).append(' ');
          break;
        case THROWS_LIST:
          buffer.append(PsiKeyword.THROWS).append(' ');
          break;
        case PROVIDES_WITH_LIST:
          buffer.append(PsiKeyword.WITH).append(' ');
          break;
      }
      for (int i = 0; i < types.length; i++) {
        if (i > 0) buffer.append(role == Role.EXTENDS_BOUNDS_LIST ? " & " : ", ");
        buffer.append(types[i].getCanonicalText(true));
      }
    }
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);
    PsiJavaCodeReferenceElement[] mirrorRefs = SourceTreeToPsiMap.<PsiReferenceList>treeToPsiNotNull(element).getReferenceElements();
    PsiJavaCodeReferenceElement[] stubRefs = getReferenceElements();
    if (mirrorRefs.length == 0 && stubRefs.length == 1 && CommonClassNames.JAVA_LANG_OBJECT.equals(stubRefs[0].getQualifiedName())) {
      // annotated Object type is supported in stubs but not supported in decompiler yet
      return;
    }
    setMirrors(stubRefs, mirrorRefs);
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
    return "PsiReferenceList:" + getRole();
  }
}