// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.RootsProvider;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public final class PackageElement implements Queryable, RootsProvider {
  public static final DataKey<PackageElement> DATA_KEY =  DataKey.create("package.element");

  private final @Nullable Module myModule;
  private final @NotNull PsiPackage myElement;
  private final boolean myIsLibraryElement;

  public PackageElement(@Nullable Module module, @NotNull PsiPackage element, boolean isLibraryElement) {
    myModule = module;
    myElement = element;
    myIsLibraryElement = isLibraryElement;
  }

  public @Nullable Module getModule() {
    return myModule;
  }

  public @NotNull PsiPackage getPackage() {
    return myElement;
  }

  @Override
  public @NotNull Collection<VirtualFile> getRoots() {
    Set<VirtualFile> roots= new HashSet<>();
    final PsiDirectory[] dirs = PackageUtil.getDirectories(getPackage(), myModule, isLibraryElement());
    for (PsiDirectory each : dirs) {
      roots.add(each.getVirtualFile());
    }
    return roots;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    return o instanceof PackageElement packageElement && 
           myIsLibraryElement == packageElement.myIsLibraryElement &&
           myElement.equals(packageElement.myElement) &&
           Objects.equals(myModule, packageElement.myModule);
  }

  @Override
  public int hashCode() {
    int result = myModule != null ? myModule.hashCode() : 0;
    result = 29 * result + myElement.hashCode();
    result = 29 * result + (myIsLibraryElement ? 1 : 0);
    return result;
  }

  public boolean isLibraryElement() {
    return myIsLibraryElement;
  }



  @Override
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
    PsiPackage pkg = getPackage();
    if (pkg instanceof Queryable) {
      ((Queryable)pkg).putInfo(info);
    }
  }
}
