package com.intellij.ide.projectView.impl.nodes;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiPackage;

/**
 * @author Eugene Zhuravlev
 * Date: Sep 19, 2003
 * Time: 3:51:02 PM
 */
public final class PackageElement {
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
}
