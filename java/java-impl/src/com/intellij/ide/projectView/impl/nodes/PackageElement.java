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
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.Queryable;
import com.intellij.psi.PsiPackage;

import java.util.Map;

/**
 * @author Eugene Zhuravlev
 * Date: Sep 19, 2003
 * Time: 3:51:02 PM
 */
public final class PackageElement implements Queryable {
  public static final DataKey<PackageElement> DATA_KEY =  DataKey.create("package.element");

  private final Module myModule;
  private final PsiPackage myElement;
  private final boolean myIsLibraryElement;

  public PackageElement(Module module, PsiPackage element, boolean isLibraryElement) {
    myModule = module;
    myElement = element;
    myIsLibraryElement = isLibraryElement;
  }

  public Module getModule() {
    return myModule;
  }

  public PsiPackage getPackage() {
    return myElement;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PackageElement)) return false;

    final PackageElement packageElement = (PackageElement)o;

    if (myIsLibraryElement != packageElement.myIsLibraryElement) return false;
    if (myElement != null ? !myElement.equals(packageElement.myElement) : packageElement.myElement != null) return false;
    if (myModule != null ? !myModule.equals(packageElement.myModule) : packageElement.myModule != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myModule != null ? myModule.hashCode() : 0);
    result = 29 * result + (myElement != null ? myElement.hashCode() : 0);
    result = 29 * result + (myIsLibraryElement ? 1 : 0);
    return result;
  }

  public boolean isLibraryElement() {
    return myIsLibraryElement;
  }

  public void putInfo(Map<String, String> info) {
    PsiPackage pkg = getPackage();
    if (pkg instanceof Queryable) {
      ((Queryable)pkg).putInfo(info);
    }
  }
}
