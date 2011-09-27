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
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

class ClsPackageStatementImpl extends ClsElementImpl implements PsiPackageStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsPackageStatementImpl");

  private final ClsFileImpl myFile;
  private final String myPackageName;

  public ClsPackageStatementImpl(@NotNull ClsFileImpl file) {
    myFile = file;
    final PsiClass[] psiClasses = file.getClasses();
    String className = psiClasses.length > 0 ? psiClasses[0].getQualifiedName() : "";
    int index = className.lastIndexOf('.');
    myPackageName = index < 0 ? null : className.substring(0, index);
  }

  public PsiElement getParent() {
    return myFile;
  }

  /**
   * @not_implemented
   */
  public PsiJavaCodeReferenceElement getPackageReference() {
    LOG.error("method not implemented");
    return null;
  }

  /**
   * @not_implemented
   */
  public PsiModifierList getAnnotationList() {
    LOG.error("method not implemented");
    return null;
  }

  /**
   * @not_implemented
   */
  @NotNull
  public PsiElement[] getChildren() {
    LOG.error("method not implemented");
    return null;
  }

  public String getPackageName() {
    return myPackageName;
  }

  public void appendMirrorText(final int indentLevel, final StringBuilder buffer) {
    buffer.append("package ");
    buffer.append(getPackageName());
    buffer.append(";");
  }

  public void setMirror(@NotNull TreeElement element) {
    setMirrorCheckingType(element, ElementType.PACKAGE_STATEMENT);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitPackageStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiPackageStatement:" + getPackageName();
  }
}
