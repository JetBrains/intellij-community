// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.dir;

import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class DirDiffFrame extends FrameWrapper {

  public DirDiffFrame(Project project, DirDiffTableModel model) {
    super(project, "DirDiffDialog");
    setSize(JBUI.size(800, 600));
    setTitle(model.getTitle());
    DirDiffPanel panel = new DirDiffPanel(model, new DirDiffWindow.Frame(this));
    Disposer.register(this, panel);
    setComponent(UiDataProvider.wrapComponent(panel.getPanel(), sink -> {
      sink.set(PlatformCoreDataKeys.HELP_ID, "reference.dialogs.diff.folder");
    }));
    setPreferredFocusedComponent(panel.getTable());
    closeOnEsc();
  }
}
