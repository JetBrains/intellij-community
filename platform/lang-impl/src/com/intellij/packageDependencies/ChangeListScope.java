// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packageDependencies;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.psi.search.scope.packageSet.FilteredNamedScope;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.vcs.changes.ChangesUtil.getAfterRevisionsFiles;
import static java.util.stream.Collectors.toList;

public final class ChangeListScope extends FilteredNamedScope {
  public static final String NAME = IdeBundle.message("scope.modified.files");

  public ChangeListScope(@NotNull ChangeListManager manager) {
    super(NAME, AllIcons.Toolwindows.ToolWindowChanges, 0, manager.getAffectedFiles());
  }

  public ChangeListScope(@NotNull ChangeList list) {
    super(list.getName(), AllIcons.Toolwindows.ToolWindowChanges, 0, getAfterRevisionsFiles(list.getChanges().stream()).collect(toList()));
  }
}
