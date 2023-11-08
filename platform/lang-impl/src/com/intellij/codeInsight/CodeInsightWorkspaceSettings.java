// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.model.SideEffectGuard;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

@Service(Service.Level.PROJECT)
@State(name = "CodeInsightWorkspaceSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class CodeInsightWorkspaceSettings extends SimpleModificationTracker implements PersistentStateComponent<CodeInsightWorkspaceSettings> {
  private boolean optimizeImportsOnTheFly;

  public static CodeInsightWorkspaceSettings getInstance(@NotNull Project project) {
    return project.getService(CodeInsightWorkspaceSettings.class);
  }

  @OptionTag
  public boolean isOptimizeImportsOnTheFly() {
    return optimizeImportsOnTheFly;
  }

  public void setOptimizeImportsOnTheFly(boolean value) {
    if (optimizeImportsOnTheFly != value) {
      SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.SETTINGS);
      optimizeImportsOnTheFly = value;
      incModificationCount();
    }
  }

  @TestOnly
  public void setOptimizeImportsOnTheFly(boolean optimizeImportsOnTheFly, Disposable parentDisposable) {
    boolean prev = this.optimizeImportsOnTheFly;
    this.optimizeImportsOnTheFly = optimizeImportsOnTheFly;
    Disposer.register(parentDisposable, () -> {
      this.optimizeImportsOnTheFly = prev;
    });
  }

  @Override
  public void noStateLoaded() {
    //noinspection deprecation
    optimizeImportsOnTheFly = CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY;
    incModificationCount();
  }

  @Override
  public void loadState(@NotNull CodeInsightWorkspaceSettings state) {
    optimizeImportsOnTheFly = state.optimizeImportsOnTheFly;
  }

  @Override
  public @NotNull CodeInsightWorkspaceSettings getState() {
    return this;
  }
}
