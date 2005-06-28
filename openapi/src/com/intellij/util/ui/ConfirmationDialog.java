package com.intellij.util.ui;

import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.project.Project;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConfirmationDialog extends OptionsMessageDialog{

  private final VcsShowConfirmationOption myOption;

  public static boolean requestForConfirmation(@NotNull VcsShowConfirmationOption option,
                                               @NotNull Project project,
                                               @NotNull String message,
                                               @NotNull String title,
                                               @Nullable Icon icon) {
    if (option.getValue() == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) return false;
    final ConfirmationDialog dialog = new ConfirmationDialog(project, message, title, icon, option);
    dialog.show();
    return dialog.isOK();
  }

  public ConfirmationDialog(Project project, final String message, String title, final Icon icon, final VcsShowConfirmationOption option) {
    super(project, message, title, icon);
    myOption = option;
    init();    
  }

  protected String getOkActionName() {
    return "&Yes";
  }

  protected String getCancelActionName() {
    return "&No";
  }

  protected boolean isToBeShown() {
    return myOption.getValue() == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
  }

  protected void setToBeShown(boolean value, boolean onOk) {
    final VcsShowConfirmationOption.Value optionValue;

    if (value) {
      optionValue = VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
    } else {
      if (onOk) {
        optionValue = VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY;
      } else {
        optionValue = VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY;
      }
    }

    myOption.setValue(optionValue);

  }

  protected boolean shouldSaveOptionsOnCancel() {
    return true;
  }
}
