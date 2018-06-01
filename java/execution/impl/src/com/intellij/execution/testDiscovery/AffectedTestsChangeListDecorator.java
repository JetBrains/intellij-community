// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.execution.testDiscovery.actions.ShowDiscoveredTestsAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListDecorator;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;

import static com.intellij.ui.SimpleTextAttributes.STYLE_UNDERLINE;

public class AffectedTestsChangeListDecorator implements ChangeListDecorator {
  private final Project myProject;

  public AffectedTestsChangeListDecorator(final Project project) {
    myProject = project;
  }

  @Override
  public void decorateChangeList(LocalChangeList changeList,
                                 ColoredTreeCellRenderer renderer,
                                 boolean selected,
                                 boolean expanded,
                                 boolean hasFocus) {
    if (!Registry.is("show.affected.tests.in.changelists")) return;
    if (!ShowDiscoveredTestsAction.isEnabled()) return;
    if (changeList.getChanges().isEmpty()) return;

    renderer.append(", ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    renderer.append("show affected tests", new SimpleTextAttributes(STYLE_UNDERLINE, UIUtil.getInactiveTextColor()), (Runnable)() -> {
      DataContext dataContext = DataManager.getInstance().getDataContext(renderer.getTree());
      Change[] objects = ArrayUtil.toObjectArray(changeList.getChanges(), Change.class);
      ShowDiscoveredTestsAction.showDiscoveredTestsByChanges(myProject, objects, changeList.getName(), dataContext);
    });
  }
}