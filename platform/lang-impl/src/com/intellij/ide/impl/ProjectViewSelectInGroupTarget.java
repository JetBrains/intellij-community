// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.impl;

import com.intellij.ide.CompositeSelectInTarget;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.impl.SelectInProjectViewImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;

import static com.intellij.ide.impl.ProjectViewSelectInTargetProviderKt.getProjectViewSelectInTargets;


public class ProjectViewSelectInGroupTarget implements CompositeSelectInTarget, DumbAware {
  @Override
  public @NotNull @Unmodifiable Collection<SelectInTarget> getSubTargets(@NotNull SelectInContext context) {
    var result = new ArrayList<>(getProjectViewSelectInTargets(context.getProject()));
    // The provider puts the current one first, for the purpose of selectInAnyTarget,
    // but to list all the targets we need a consistent order without that hack.
    result.sort(Comparator.comparing(SelectInTarget::getWeight));
    return result;
  }

  @Override
  public boolean canSelect(SelectInContext context) {
    Collection<SelectInTarget> targets = getProjectViewSelectInTargets(context.getProject());
    for (SelectInTarget projectViewTarget : targets) {
      if (projectViewTarget.canSelect(context)) return true;
    }
    return false;
  }

  @Override
  public void selectIn(final SelectInContext context, final boolean requestFocus) {
    Collection<SelectInTarget> targetsToCheck = getProjectViewSelectInTargets(context.getProject());
    context.getProject().getService(SelectInProjectViewImpl.class).selectInAnyTarget(context, targetsToCheck, requestFocus);
  }

  @Override
  public String getToolWindowId() {
    return ToolWindowId.PROJECT_VIEW;
  }

  @Override
  public String getMinorViewId() {
    return null;
  }

  @Override
  public String toString() {
    return IdeUICustomization.getInstance().projectMessage("select.in.item.project.view");
  }
}
