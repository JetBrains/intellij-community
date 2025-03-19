// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.impl;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.PossiblyDumbAware;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
public final class ProjectSubViewSelectInTarget implements SelectInTarget, PossiblyDumbAware {
  private final ProjectViewSelectInTarget myBaseTarget;
  private final String mySubId;
  private final int myWeight;

  public ProjectSubViewSelectInTarget(ProjectViewSelectInTarget target, String subId, int weight) {
    myBaseTarget = target;
    mySubId = subId;
    myWeight = weight;
  }

  @Override
  public boolean canSelect(SelectInContext context) {
    return myBaseTarget.isSubIdSelectable(mySubId, context);
  }

  @Override
  public void selectIn(SelectInContext context, boolean requestFocus) {
    myBaseTarget.setSubId(mySubId);
    myBaseTarget.selectIn(context, requestFocus);
  }

  @Override
  public String getToolWindowId() {
    return myBaseTarget.getToolWindowId();
  }

  @Override
  public String getMinorViewId() {
    return myBaseTarget.getMinorViewId();
  }

  @Override
  public float getWeight() {
    return myWeight;
  }

  @Override
  public String toString() {
    return myBaseTarget.getSubIdPresentableName(mySubId);
  }

  @Override
  public boolean isDumbAware() {
    return DumbService.isDumbAware(myBaseTarget);
  }
}
