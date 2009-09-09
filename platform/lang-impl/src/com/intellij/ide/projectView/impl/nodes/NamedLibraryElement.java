package com.intellij.ide.projectView.impl.nodes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.Comparing;

/**
 * @author Eugene Zhuravlev
 * Date: Sep 17, 2003
 * Time: 7:08:30 PM
 */
public final class NamedLibraryElement {
  private final Module myContextModule;
  private final OrderEntry myEntry;

  public NamedLibraryElement(Module parent, OrderEntry entry) {
    myContextModule = parent;
    myEntry = entry;
  }

  public Module getModule() {
    return myContextModule;
  }

  public String getName() {
    return myEntry.getPresentableName();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NamedLibraryElement)) return false;

    final NamedLibraryElement namedLibraryElement = (NamedLibraryElement)o;

    if (!myEntry.equals(namedLibraryElement.myEntry)) return false;
    if (Comparing.equal(myContextModule, namedLibraryElement.myContextModule)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myContextModule != null ? myContextModule.hashCode() : 0;
    result = 29 * result + myEntry.hashCode();
    return result;
  }

  public OrderEntry getOrderEntry() {
    return myEntry;
  }
}
