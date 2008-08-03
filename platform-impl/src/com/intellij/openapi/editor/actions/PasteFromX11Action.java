package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.diagnostic.Logger;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/**
 * Author: msk
 */
public class PasteFromX11Action extends EditorAction {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actions.PasteFromX11Action");

  public PasteFromX11Action() {
    super(new Handler());
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    DataContext dataContext = e.getDataContext();
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor == null || !SystemInfo.X11PasteEnabledSystem) {
      presentation.setEnabled(false);
    }
    else {
      boolean rightPlace = true;
      final InputEvent inputEvent = e.getInputEvent();
      if (inputEvent instanceof MouseEvent) {
        rightPlace = editor.getMouseEventArea((MouseEvent)inputEvent) == EditorMouseEventArea.EDITING_AREA;
      }
      presentation.setEnabled(rightPlace);
    }
  }

  public static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      final Clipboard clip = editor.getComponent().getToolkit().getSystemSelection();
      if (clip != null) {
        Transferable res = null;
        try {
          res = clip.getContents(null);
        }
        catch (Exception e) {
          if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
            LOG.info(e);
            Messages.showErrorDialog(editor.getProject(), "Cannot paste from X11 clipboard: " + e.getLocalizedMessage(), "Cannot Paste");
            return;
          }
        }
        editor.putUserData(EditorEx.LAST_PASTED_REGION, EditorModificationUtil.pasteFromTransferrable(res, editor));
      }
    }
  }
}

