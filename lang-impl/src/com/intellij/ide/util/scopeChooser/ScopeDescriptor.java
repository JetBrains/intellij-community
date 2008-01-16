/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.ide.util.scopeChooser;

import com.intellij.psi.search.SearchScope;

public class ScopeDescriptor {
  private SearchScope myScope;

  public ScopeDescriptor(SearchScope scope) {
    myScope = scope;
  }

  public String getDisplay() {
    return myScope.getDisplayName();
  }

  public SearchScope getScope() {
    return myScope;
  }
}