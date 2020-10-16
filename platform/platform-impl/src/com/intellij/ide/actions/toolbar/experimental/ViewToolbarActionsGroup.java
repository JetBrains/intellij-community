// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.toolbar.experimental;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;


public class ViewToolbarActionsGroup extends DefaultActionGroup implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    var isEnabled = !Registry.is("ide.new.navbar", false);
    e.getPresentation().setEnabledAndVisible(isEnabled);
    Arrays.stream(getChildren(e)).forEach(action -> action.getTemplatePresentation().setEnabledAndVisible(isEnabled));
  }
}
