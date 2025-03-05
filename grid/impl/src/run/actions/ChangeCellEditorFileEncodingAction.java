package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.ChangeFileEncodingAction;
import org.jetbrains.annotations.NotNull;

public class ChangeCellEditorFileEncodingAction extends ChangeFileEncodingAction {
  public static final DataKey<Boolean> ENCODING_CHANGE_SUPPORTED_KEY = DataKey.create("EncodingChangeSupported");

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!Boolean.TRUE.equals(e.getData(ENCODING_CHANGE_SUPPORTED_KEY))) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    super.update(e);

    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    String text = DataGridBundle.message("action.change.encoding.text", file != null ? " (" + file.getCharset().displayName() + ")" : "");
    e.getPresentation().setText(text);
  }
}
