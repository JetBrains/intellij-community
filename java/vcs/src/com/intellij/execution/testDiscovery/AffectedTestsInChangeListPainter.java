// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testDiscovery;

import com.intellij.execution.testDiscovery.actions.ShowAffectedTestsAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListDecorator;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.NamedColorUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.ui.SimpleTextAttributes.STYLE_UNDERLINE;

final class AffectedTestsInChangeListPainter implements ChangeListDecorator {
  private final Project myProject;

  AffectedTestsInChangeListPainter(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void decorateChangeList(@NotNull LocalChangeList changeList,
                                 @NotNull ColoredTreeCellRenderer renderer,
                                 boolean selected,
                                 boolean expanded,
                                 boolean hasFocus) {
    if (!Registry.is("show.affected.tests.in.changelists")) return;
    if (!ShowAffectedTestsAction.isEnabled(myProject)) return;
    if (changeList.getChanges().isEmpty()) return;

    renderer.append(", ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    renderer.append(JavaCompilerBundle.message("test.discovery.find.affected.tests"), new SimpleTextAttributes(STYLE_UNDERLINE, NamedColorUtil.getInactiveTextColor()), (Runnable)() -> {
      DataContext dataContext = DataManager.getInstance().getDataContext(renderer.getTree());
      Change[] changes = changeList.getChanges().toArray(Change.EMPTY_CHANGE_ARRAY);
      ShowAffectedTestsAction.showDiscoveredTestsByChanges(myProject, changes, changeList.getName(), dataContext);
    });
  }
}