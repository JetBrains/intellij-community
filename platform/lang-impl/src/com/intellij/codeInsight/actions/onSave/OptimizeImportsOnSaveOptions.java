// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@Service
@State(name = "OptimizeOnSaveOptions", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class OptimizeImportsOnSaveOptions extends FormatOnSaveOptionsBase<OptimizeImportsOnSaveOptions.State>
  implements PersistentStateComponent<OptimizeImportsOnSaveOptions.State>, Cloneable {


  public static @NotNull OptimizeImportsOnSaveOptions getInstance(@NotNull Project project) {
    return project.getService(OptimizeImportsOnSaveOptions.class);
  }


  static class State extends FormatOnSaveOptionsBase.StateBase implements Cloneable {
    State() {
      super(DefaultsProvider::getFileTypesWithOptimizeImportsOnSaveByDefault);
    }

    @Override
    public State clone() {
      return (State)super.clone();
    }
  }

  private final @NotNull Project myProject;

  public OptimizeImportsOnSaveOptions(@NotNull Project project) {
    super(new State());
    myProject = project;
  }

  @Override
  protected void convertOldProperties() {
    String oldOptimizeImportsOnSaveProperty = "optimize.imports.on.save";
    boolean optimizeAllOld = PropertiesComponent.getInstance(myProject).getBoolean(oldOptimizeImportsOnSaveProperty);
    if (optimizeAllOld) {
      setRunOnSaveEnabled(true);
      setRunForAllFileTypes();
    }
    PropertiesComponent.getInstance(myProject).unsetValue(oldOptimizeImportsOnSaveProperty);
  }

  @Override
  public OptimizeImportsOnSaveOptions clone() {
    return (OptimizeImportsOnSaveOptions)super.clone();
  }
}
