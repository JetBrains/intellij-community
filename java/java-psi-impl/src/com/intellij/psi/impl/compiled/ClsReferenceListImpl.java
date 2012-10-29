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
import com.intellij.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"HardCodedStringLiteral"})
public class ClsReferenceListImpl extends ClsRepositoryPsiElement<PsiClassReferenceListStub> implements PsiReferenceList {
  private static final ClsJavaCodeReferenceElementImpl[] EMPTY_REFS_ARRAY = new ClsJavaCodeReferenceElementImpl[0];

  private ClsJavaCodeReferenceElementImpl[] myRefs;

  public ClsReferenceListImpl(@NotNull PsiClassReferenceListStub stub) {
    super(stub);
  }

  @Override
  @NotNull
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    synchronized (LAZY_BUILT_LOCK) {
      if (myRefs == null) {
        final String[] strings = getStub().getReferencedNames();
        if (strings.length > 0) {
          myRefs = new ClsJavaCodeReferenceElementImpl[strings.length];
          for (int i = 0; i < strings.length; i++) {
            myRefs[i] = new ClsJavaCodeReferenceElementImpl(this, strings[i]);
          }
        }
        else {
          myRefs = EMPTY_REFS_ARRAY;
        }
      }
      return myRefs;
    }
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    return getReferenceElements();
  }

  @Override
  @NotNull
  public PsiClassType[] getReferencedTypes() {
    return getStub().getReferencedTypes();
  }

  @Override
  public Role getRole() {
    return getStub().getRole();
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    final String[] names = getStub().getReferencedNames();
    if (names.length != 0) {
      final Role role = getStub().getRole();
      switch (role) {
        case EXTENDS_BOUNDS_LIST:
        case EXTENDS_LIST:
          buffer.append("extends ");
          break;
        case IMPLEMENTS_LIST:
          buffer.append("implements ");
          break;
        case THROWS_LIST:
          buffer.append("throws ");
          break;
      }
      for (int i = 0; i < names.length; i++) {
        if (i > 0) buffer.append(", ");
        buffer.append(names[i]);
      }
    }
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);
    setMirrors(getReferenceElements(), SourceTreeToPsiMap.<PsiReferenceList>treeToPsiNotNull(element).getReferenceElements());
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
    return "PsiReferenceList";
  }
}
