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
package com.intellij.packageDependencies.ui;

import com.intellij.cyclicDependencies.ui.CyclicDependenciesPanel;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPackage;
import com.intellij.util.PlatformIcons;

import javax.swing.*;
import java.util.Map;
import java.util.Set;

public class PackageNode extends PackageDependenciesNode {

  private String myPackageName;
  private final String myPackageQName;
  private final PsiPackage myPackage;


  public PackageNode(PsiPackage aPackage, boolean showFQName) {
    super(aPackage.getProject());
    myPackage = aPackage;
    myPackageName = showFQName ? aPackage.getQualifiedName() : aPackage.getName();
    if (myPackageName == null || myPackageName.length() == 0) {
      myPackageName = CyclicDependenciesPanel.DEFAULT_PACKAGE_ABBREVIATION;
    }
    String packageQName = aPackage.getQualifiedName();
    if (packageQName.length() == 0) {
      packageQName = null;
    }
    myPackageQName = packageQName;
  }

  public void fillFiles(Set<PsiFile> set, boolean recursively) {
    super.fillFiles(set, recursively);
    int count = getChildCount();
    for (int i = 0; i < count; i++) {
      PackageDependenciesNode child = (PackageDependenciesNode)getChildAt(i);
      if (child instanceof FileNode || recursively) {
        child.fillFiles(set, true);
      }
    }
  }

  public String toString() {
    return myPackageName;
  }

  public void setPackageName(final String packageName) {
    myPackageName = packageName;
  }

  public String getPackageQName() {
    return myPackageQName;
  }

  public PsiElement getPsiElement() {
    return myPackage;
  }

  public int getWeight() {
    return 3;
  }

  public boolean equals(Object o) {
    if (isEquals()){
      return super.equals(o);
    }
    if (this == o) return true;
    if (!(o instanceof PackageNode)) return false;

    final PackageNode packageNode = (PackageNode)o;

    if (!myPackageName.equals(packageNode.myPackageName)) return false;
    if (myPackageQName != null ? !myPackageQName.equals(packageNode.myPackageQName) : packageNode.myPackageQName != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myPackageName.hashCode();
    result = 29 * result + (myPackageQName != null ? myPackageQName.hashCode() : 0);
    return result;
  }

  public Icon getIcon() {
    return PlatformIcons.PACKAGE_ICON;
  }


  public boolean isValid() {
    return myPackage != null && myPackage.isValid();
  }

  @Override
  public boolean canSelectInLeftTree(final Map<PsiFile, Set<PsiFile>> deps) {
    Set<PsiFile> files = deps.keySet();
    String packageName = myPackageQName;
    for (PsiFile file : files) {
      if (file instanceof PsiJavaFile && Comparing.equal(packageName, ((PsiJavaFile)file).getPackageName())) {
        return true;
      }
    }
    return false;
  }
}
