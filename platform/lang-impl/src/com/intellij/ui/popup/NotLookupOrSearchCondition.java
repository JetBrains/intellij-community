// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui.popup;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ex.WindowManagerEx;

import java.awt.*;

/**
 * @author yole
 */
public final class NotLookupOrSearchCondition implements Condition<Project> {
  public static NotLookupOrSearchCondition INSTANCE = new NotLookupOrSearchCondition();

  private NotLookupOrSearchCondition() {
  }

  @Override
  public boolean value(final Project project) {
    final Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(project);
    boolean fromQuickSearch =  focusedComponent != null && focusedComponent.getParent() instanceof ChooseByNameBase.JPanelProvider;
    return !fromQuickSearch && LookupManager.getInstance(project).getActiveLookup() == null;
  }
}