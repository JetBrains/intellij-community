// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packageDependencies;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.psi.search.scope.packageSet.FilteredNamedScope;
import org.jetbrains.annotations.NotNull;

public final class ChangeListScope extends FilteredNamedScope {
  public static final String NAME = IdeBundle.message("scope.modified.files");
  private final boolean all; // TODO: use different icons instead

  public ChangeListScope(@NotNull ChangeListManager manager) {
    super(NAME, AllIcons.Toolwindows.ToolWindowChanges, 0, manager::isFileAffected);
    all = true;
  }

  public ChangeListScope(@NotNull ChangeListManager manager, @NotNull String name) {
    super(name, AllIcons.Toolwindows.ToolWindowChanges, 0,
          file -> manager.getChangeLists(file).stream().anyMatch(list -> list.getName().equals(name)));
    all = false;
  }

  @Override
  public int hashCode() {
    return getName().hashCode();
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) return true;
    if (object instanceof ChangeListScope) {
      ChangeListScope scope = (ChangeListScope)object;
      return scope.all == all && scope.getName().equals(getName());
    }
    return false;
  }

  @Override
  public String toString() {
    String string = super.toString();
    if (all) string += "; ALL";
    return string;
  }
}
