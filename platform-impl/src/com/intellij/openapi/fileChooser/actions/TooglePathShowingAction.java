package com.intellij.openapi.fileChooser.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.Nullable;

public class TooglePathShowingAction extends AnAction implements DumbAware {

  {
    setEnabledInModalContext(true);
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setText(IdeBundle.message("file.chooser.hide.path.tooltip.text"));
    final FileChooserDialogImpl dialog = getDialog(e);
    e.getPresentation().setEnabled(dialog != null);
  }

  private @Nullable
  FileChooserDialogImpl getDialog(final AnActionEvent e) {
    return e.getData(FileChooserDialogImpl.KEY);
  }

  public void actionPerformed(final AnActionEvent e) {
    final FileChooserDialogImpl dialog = getDialog(e);
    if (dialog != null) {
      dialog.toggleShowTextField();
    }
  }


}
