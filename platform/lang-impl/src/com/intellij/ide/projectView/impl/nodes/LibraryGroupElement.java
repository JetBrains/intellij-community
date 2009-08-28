package com.intellij.ide.projectView.impl.nodes;

import com.intellij.openapi.module.Module;

/**
 * @author Eugene Zhuravlev
 * Date: Sep 17, 2003
 * Time: 7:08:03 PM
 */
public final class LibraryGroupElement {
  private final Module myModule;

  public LibraryGroupElement(Module module) {
    myModule = module;
  }

  public Module getModule() {
    return myModule;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LibraryGroupElement)) return false;

    final LibraryGroupElement libraryGroupElement = (LibraryGroupElement)o;

    if (!myModule.equals(libraryGroupElement.myModule)) return false;

    return true;
  }

  public int hashCode() {
    return myModule.hashCode();
  }
}
