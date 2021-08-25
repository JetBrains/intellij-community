// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
     * This method is called right after the dialog is {@link #close(int) closed}.
     * <br/>
     * Note that this method won't be called in the case when the dialog is closed by {@link #CANCEL_EXIT_CODE Cancel}
     * if {@link #shouldSaveOptionsOnCancel() saving the choice on cancel is disabled} (which is by default).
     *
     * @param isSelected true if user selected "don't show again".
     * @param exitCode   the {@link #getExitCode() exit code} of the dialog.
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
   * @return default selection state of checkbox (false -> checkbox selected)
   */
  boolean isToBeShown();

  /**
   * @param toBeShown - if dialog should be shown next time (checkbox selected -> false)
   * @param exitCode  of corresponding DialogWrapper
   */
  void setToBeShown(boolean toBeShown, int exitCode);

  /**
   * @return true if checkbox should be shown
   */
  boolean canBeHidden();

  boolean shouldSaveOptionsOnCancel();

  @NotNull
  @NlsContexts.Checkbox
  String getDoNotShowMessage();
}
