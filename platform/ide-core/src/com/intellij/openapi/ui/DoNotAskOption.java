// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

/**
 * @see Adapter
 */
public interface DoNotAskOption {

  abstract class Adapter implements DoNotAskOption {
    /**
     * Save the state of the checkbox in the settings, or perform some other related action.
     * This method is called right after the dialog is closed, see {@link DialogWrapper#close(int)}.
     * <p>
     * If the dialog is closed by {@link DialogWrapper#CANCEL_EXIT_CODE},
     * this method is not called by default.
     * To call it even in this case, override {@link #shouldSaveOptionsOnCancel()} to return {@code true}.
     *
     * @param isSelected true if user selected "don't show again".
     * @param exitCode   the exit code of the dialog, see {@code DialogWrapper.getExitCode}.
     * @see #shouldSaveOptionsOnCancel()
     */
    public abstract void rememberChoice(boolean isSelected, int exitCode);

    /**
     * Tells whether the checkbox should be selected by default or not.
     *
     * @return true if the checkbox should be selected by default.
     */
    public boolean isSelectedByDefault() {
      return false;
    }

    @Override
    public boolean shouldSaveOptionsOnCancel() {
      return false;
    }

    @Override
    public @NotNull String getDoNotShowMessage() {
      return IdeCoreBundle.message("dialog.options.do.not.ask");
    }

    @Override
    public final boolean isToBeShown() {
      return !isSelectedByDefault();
    }

    @Override
    public final void setToBeShown(boolean toBeShown, int exitCode) {
      rememberChoice(!toBeShown, exitCode);
    }

    @Override
    public final boolean canBeHidden() {
      return true;
    }
  }

  /**
   * @return default selection state of checkbox (false means the checkbox is selected)
   */
  boolean isToBeShown();

  /**
   * @param toBeShown if dialog should be shown next time (checkbox selected means false)
   * @param exitCode  from the corresponding DialogWrapper
   */
  void setToBeShown(boolean toBeShown, int exitCode);

  /**
   * @return true if the checkbox "Do not ask again" should be shown
   */
  boolean canBeHidden();

  boolean shouldSaveOptionsOnCancel();

  @NotNull
  @NlsContexts.Checkbox
  String getDoNotShowMessage();
}
