// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.scopeChooser;

import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * @author anna
 * @since 16-Jan-2008
 */
public class ScopeDescriptor {
  private final SearchScope myScope;

  public ScopeDescriptor(@Nullable SearchScope scope) {
    myScope = scope;
  }

  public String getDisplay() {
    return myScope == null ? null : myScope.getDisplayName();
  }

  @Nullable
  public Icon getDisplayIcon() {
    return myScope == null ? null : myScope.getDisplayIcon();
  }

  public SearchScope getScope() {
    return myScope;
  }
}
