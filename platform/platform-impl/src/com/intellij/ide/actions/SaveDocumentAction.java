package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;

/**
 * @author yole
 */
public class SaveDocumentAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Document doc = getDocument(e);
    if (doc != null) {
      FileDocumentManager.getInstance().saveDocument(doc);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(getDocument(e) != null);
  }

  private static Document getDocument(AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    return editor != null ? editor.getDocument() : null;
  }
}
