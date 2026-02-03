// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.ide.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ModuleStructureComponent extends SimpleToolWindowPanel implements Disposable, UiDataProvider {
  private final ModuleStructurePane myStructurePane;

  public ModuleStructureComponent(Module module) {
    super(true, true);

    myStructurePane = new ModuleStructurePane(module);
    Disposer.register(this, myStructurePane);

    setContent(myStructurePane.createComponent());
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    myStructurePane.uiDataSnapshot(sink);
  }

  @Override
  public void dispose() {

  }
}
