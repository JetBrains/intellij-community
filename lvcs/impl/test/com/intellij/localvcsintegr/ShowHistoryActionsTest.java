package com.intellij.localvcsintegr;

import com.intellij.localvcs.integration.ui.actions.ShowHistoryAction;
import com.intellij.localvcs.integration.ui.actions.ShowSelectionHistoryAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
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

  private void assertStatus(AnAction a, VirtualFile f, boolean isEnabled) {
    AnActionEvent e = createEventFor(a, f);
    a.update(e);
    assertEquals(isEnabled, e.getPresentation().isEnabled());
  }

  private AnActionEvent createEventFor(AnAction a, final VirtualFile f) {
    DataContext dc = new DataContext() {
      @Nullable
      public Object getData(String id) {
        if (id.equals(DataKeys.VIRTUAL_FILE.getName())) return f;
        if (id.equals(DataKeys.PROJECT.getName())) return myProject;
        throw new RuntimeException();
      }
    };
    return new AnActionEvent(null, dc, "", a.getTemplatePresentation(), null, -1);
  }
}