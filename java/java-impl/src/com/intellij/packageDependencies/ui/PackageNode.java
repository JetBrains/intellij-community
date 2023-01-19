// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies.ui;

import com.intellij.cyclicDependencies.ui.CyclicDependenciesPanel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;

import javax.swing.*;
import java.util.Map;
import java.util.Objects;
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
      myPackageName = CyclicDependenciesPanel.getDefaultPackageAbbreviation();
    }
    String packageQName = aPackage.getQualifiedName();
    if (packageQName.length() == 0) {
      packageQName = null;
    }
    myPackageQName = packageQName;
  }

  @Override
  public void fillFiles(Set<? super PsiFile> set, boolean recursively) {
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

  @Override
  public PsiElement getPsiElement() {
    return myPackage;
  }

  @Override
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

  @Override
  public Icon getIcon() {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.Package);
  }

  @Override
  public boolean isValid() {
    return myPackage != null && myPackage.isValid();
  }

  @Override
  public boolean canSelectInLeftTree(final Map<PsiFile, Set<PsiFile>> deps) {
    Set<PsiFile> files = deps.keySet();
    for (PsiFile file : files) {
      if (file instanceof PsiJavaFile && Objects.equals(myPackageQName, ((PsiJavaFile)file).getPackageName())) {
        return true;
      }
    }
    return false;
  }
}
