package com.intellij.localvcsintegr;

import com.intellij.localvcs.integration.ui.actions.ShowHistoryAction;
import com.intellij.localvcs.integration.ui.actions.ShowSelectionHistoryAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class ShowHistoryActionsTest extends IntegrationTestCase {
  public void testShowHistoryAction() {
    ShowHistoryAction a = new ShowHistoryAction();
    assertStatus(a, root, true);
    assertStatus(a, null, false);
  }

  public void testShowSelectionHistoryAction() throws IOException {
    VirtualFile f = root.createChildData(null, "f.java");

    ShowSelectionHistoryAction a = new ShowSelectionHistoryAction();
    assertStatus(a, f, true);
    assertStatus(a, root, false);
    assertStatus(a, null, false);
  }

  public void testShowSelectionHistoryActionTextForSelection() throws Exception {
    VirtualFile f = root.createChildData(null, "f.java");
    Document d = FileDocumentManager.getInstance().getDocument(f);
    d.setText("foo");
    EditorFactory ef = EditorFactory.getInstance();
    Editor editor = ef.createEditor(d);
    editor.getSelectionModel().setSelection(0, 2);

    ShowSelectionHistoryAction a = new ShowSelectionHistoryAction();
    AnActionEvent e = createEventFor(a, f, editor);
    a.update(e);
    ef.releaseEditor(editor);

    assertEquals("Show History for Selection", e.getPresentation().getText());
  }

  private void assertStatus(AnAction a, VirtualFile f, boolean isEnabled) {
    AnActionEvent e = createEventFor(a, f);
    a.update(e);
    assertEquals(isEnabled, e.getPresentation().isEnabled());
  }

  private AnActionEvent createEventFor(AnAction a, VirtualFile f) {
    return createEventFor(a, f, null);
  }

  private AnActionEvent createEventFor(AnAction a, final VirtualFile f, final Editor e) {
    DataContext dc = new DataContext() {
      @Nullable
      public Object getData(String id) {
        if (id.equals(DataKeys.VIRTUAL_FILE.getName())) return f;
        if (id.equals(DataKeys.EDITOR.getName())) return e;
        if (id.equals(DataKeys.PROJECT.getName())) return myProject;
        return null;
      }
    };
    return new AnActionEvent(null, dc, "", a.getTemplatePresentation(), null, -1);
  }
}