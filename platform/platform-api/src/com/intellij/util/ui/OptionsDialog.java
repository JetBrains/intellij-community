// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DoNotAskOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/** A dialog that includes a "Do not ask again" checkbox. */
public abstract class OptionsDialog extends DialogWrapper {
  protected final Project myProject;

  private class MyDoNotAsk implements DoNotAskOption {
    @Override
    public boolean isToBeShown() {
      return OptionsDialog.this.isToBeShown();
    }

    @Override
    public void setToBeShown(boolean value, int exitCode) {
      OptionsDialog.this.setToBeShown(value, DialogWrapper.CANCEL_EXIT_CODE != exitCode);
    }

    @Override
    public boolean canBeHidden() {
      return OptionsDialog.this.canBeHidden();
    }

    @Override
    public boolean shouldSaveOptionsOnCancel() {
      return OptionsDialog.this.shouldSaveOptionsOnCancel();
    }

    @Override
    public @NotNull String getDoNotShowMessage() {
      return OptionsDialog.this.getDoNotShowMessage();
    }
  }

  protected OptionsDialog(@Nullable Project project) {
    super(project, true);
    myProject = project;
    setDoNotAskOption(new MyDoNotAsk());
  }

  protected OptionsDialog(Project project, boolean canBeParent) {
    super(project, canBeParent);
    myProject = project;
    setDoNotAskOption(new MyDoNotAsk());
  }

  protected OptionsDialog(boolean canBeParent) {
    super(canBeParent);
    myProject = null;
    setDoNotAskOption(new MyDoNotAsk());
  }

  protected OptionsDialog(Component parent, boolean canBeParent) {
    super(parent, canBeParent);
    myProject = null;
    setDoNotAskOption(new MyDoNotAsk());
  }

  public static boolean shiftIsPressed(int inputEventModifiers) {
    return (inputEventModifiers & Event.SHIFT_MASK) != 0;
  }

  protected abstract boolean isToBeShown();

  protected abstract void setToBeShown(boolean value, boolean onOk);

  protected boolean canBeHidden() {
    return true;
  }

  protected abstract boolean shouldSaveOptionsOnCancel();
}
