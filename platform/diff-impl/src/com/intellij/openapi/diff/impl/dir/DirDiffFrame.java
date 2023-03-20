// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.dir;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.JBUI;

/**
 * @author Konstantin Bulenkov
 */
public final class DirDiffFrame extends FrameWrapper {
  private final DirDiffPanel myPanel;

  public DirDiffFrame(Project project, DirDiffTableModel model) {
    super(project, "DirDiffDialog");
    setSize(JBUI.size(800, 600));
    setTitle(model.getTitle());
    myPanel = new DirDiffPanel(model, new DirDiffWindow.Frame(this));
    Disposer.register(this, myPanel);
    setComponent(myPanel.getPanel());
    setPreferredFocusedComponent(myPanel.getTable());
    closeOnEsc();
    DataManager.registerDataProvider(myPanel.getPanel(), dataId -> {
      if (PlatformCoreDataKeys.HELP_ID.is(dataId)) {
        return "reference.dialogs.diff.folder";
      }
      return null;
    });
  }
}