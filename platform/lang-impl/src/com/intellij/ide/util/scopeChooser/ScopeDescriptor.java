// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser;

import com.intellij.openapi.util.ColoredItem;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author anna
 */
public class ScopeDescriptor implements ColoredItem {
  private final SearchScope myScope;

  public ScopeDescriptor(@Nullable SearchScope scope) {
    myScope = scope;
  }

  public @Nullable @Nls String getDisplayName() {
    return myScope == null ? null : myScope.getDisplayName();
  }

  public @Nullable Icon getIcon() {
    return myScope == null ? null : myScope.getIcon();
  }

  public @Nullable SearchScope getScope() {
    return myScope;
  }

  public boolean scopeEquals(SearchScope scope) {
    return Comparing.equal(myScope, scope);
  }

  @Override
  public @Nullable Color getColor() {
    return myScope instanceof ColoredItem ? ((ColoredItem)myScope).getColor() : null;
  }
}
