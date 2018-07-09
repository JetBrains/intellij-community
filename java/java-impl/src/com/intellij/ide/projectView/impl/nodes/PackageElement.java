/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 */
public final class PackageElement implements Queryable, RootsProvider {
  public static final DataKey<PackageElement> DATA_KEY =  DataKey.create("package.element");

  @Nullable private final Module myModule;
  @NotNull private final PsiPackage myElement;
  private final boolean myIsLibraryElement;

  public PackageElement(@Nullable Module module, @NotNull PsiPackage element, boolean isLibraryElement) {
    myModule = module;
    myElement = element;
    myIsLibraryElement = isLibraryElement;
  }

  @Nullable
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public PsiPackage getPackage() {
    return myElement;
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getRoots() {
    Set<VirtualFile> roots= new HashSet<>();
    final PsiDirectory[] dirs = PackageUtil.getDirectories(getPackage(), myModule, isLibraryElement());
    for (PsiDirectory each : dirs) {
      roots.add(each.getVirtualFile());
    }
    return roots;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PackageElement)) return false;

    final PackageElement packageElement = (PackageElement)o;

    if (myIsLibraryElement != packageElement.myIsLibraryElement) return false;
    if (!myElement.equals(packageElement.myElement)) return false;
    if (myModule != null ? !myModule.equals(packageElement.myModule) : packageElement.myModule != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myModule != null ? myModule.hashCode() : 0);
    result = 29 * result + (myElement.hashCode());
    result = 29 * result + (myIsLibraryElement ? 1 : 0);
    return result;
  }

  public boolean isLibraryElement() {
    return myIsLibraryElement;
  }



  @Override
  public void putInfo(@NotNull Map<String, String> info) {
    PsiPackage pkg = getPackage();
    if (pkg instanceof Queryable) {
      ((Queryable)pkg).putInfo(info);
    }
  }
}
