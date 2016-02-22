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
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

class ClsPackageStatementImpl extends ClsElementImpl implements PsiPackageStatement {
  private final ClsFileImpl myFile;
  private final String myPackageName;

  public ClsPackageStatementImpl(@NotNull ClsFileImpl file) {
    myFile = file;
    String packageName = null;
    PsiClass[] psiClasses = file.getClasses();
    if (psiClasses.length > 0) {
      String className = psiClasses[0].getQualifiedName();
      if (className != null) {
        int index = className.lastIndexOf('.');
        if (index >= 0) {
          packageName = className.substring(0, index);
        }
      }
    }
    myPackageName = packageName;
  }

  @Override
  public PsiElement getParent() {
    return myFile;
  }

  @Override
  public PsiJavaCodeReferenceElement getPackageReference() {
    throw new UnsupportedOperationException("Method not implemented");
  }

  @Override
  public PsiModifierList getAnnotationList() {
    throw new UnsupportedOperationException("Method not implemented");
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    throw new UnsupportedOperationException("Method not implemented");
  }

  @Override
  public String getPackageName() {
    return myPackageName;
  }

  @Override
  public void appendMirrorText(final int indentLevel, @NotNull final StringBuilder buffer) {
    if (myPackageName != null) {
      buffer.append("package ").append(getPackageName()).append(";");
    }
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.PACKAGE_STATEMENT);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitPackageStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiPackageStatement:" + getPackageName();
  }
}