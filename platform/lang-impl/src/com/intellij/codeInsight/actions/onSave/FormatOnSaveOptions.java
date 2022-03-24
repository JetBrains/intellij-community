// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.codeInsight.actions.VcsFacade;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Service
@State(name = "FormatOnSaveOptions", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class FormatOnSaveOptions extends FormatOnSaveOptionsBase<FormatOnSaveOptions.State>
  implements PersistentStateComponent<FormatOnSaveOptions.State>, Cloneable {

  public static @NotNull FormatOnSaveOptions getInstance(@NotNull Project project) {
    return project.getService(FormatOnSaveOptions.class);
  }


  static class State extends FormatOnSaveOptionsBase.StateBase implements Cloneable {
    /**
     * Makes sense only in VCS is used in the project ({@link VcsFacade#hasActiveVcss(Project)} is <code>true</code>).
     */
    public boolean myFormatOnlyChangedLines;

    State() {
      super(DefaultsProvider::getFileTypesFormattedOnSaveByDefault);
    }

    @Override
    public State clone() {
      return (State)super.clone();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      State state = (State)o;
      return myFormatOnlyChangedLines == state.myFormatOnlyChangedLines;
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), myFormatOnlyChangedLines);
    }
  }

  private final @NotNull Project myProject;

  public FormatOnSaveOptions(@NotNull Project project) {
    super(new State());
    myProject = project;
  }

  @Override
  protected void convertOldProperties() {
    String oldFormatOnSaveProperty = "format.on.save";
    boolean formatAllOld = PropertiesComponent.getInstance(myProject).getBoolean(oldFormatOnSaveProperty);
    if (formatAllOld) {
      setRunOnSaveEnabled(true);
      setRunForAllFileTypes();
    }
    PropertiesComponent.getInstance(myProject).unsetValue(oldFormatOnSaveProperty);
  }

  public boolean isFormatOnlyChangedLines() {
    return getState().myFormatOnlyChangedLines;
  }

  void setFormatOnlyChangedLines(boolean changedLines) {
    getState().myFormatOnlyChangedLines = changedLines;
  }


  @Override
  public FormatOnSaveOptions clone() {
    return (FormatOnSaveOptions)super.clone();
  }
}
