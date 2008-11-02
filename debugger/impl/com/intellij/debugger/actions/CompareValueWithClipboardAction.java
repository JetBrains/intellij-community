package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

/*
 * Class SetValueAction
 * @author Jeka
 */
public class CompareValueWithClipboardAction extends BaseValueAction {
  protected void processText(final Project project, final String text) {
    DiffManager.getInstance().getDiffTool().show(new ClipboardSelectionContents(text, project));
  }

  private static class ClipboardSelectionContents extends DiffRequest {
    private DiffContent[] myContents = null;
    private final String myValue;

    public ClipboardSelectionContents(String value, Project project) {
      super(project);
      myValue = value;
    }

    public String[] getContentTitles() {
      return new String[] {
        DiffBundle.message("diff.content.clipboard.content.title"),
        DebuggerBundle.message("diff.content.selected.value")
      };
    }

    public DiffContent[] getContents() {
      if (myContents != null) return myContents;
      DiffContent clipboardContent = createClipboardContent();
      if (clipboardContent == null) return null;

      myContents = new DiffContent[2];
      myContents[0] = clipboardContent;

      myContents[1] = new SimpleContent(myValue);
      return myContents;
    }

    public String getWindowTitle() {
      return DebuggerBundle.message("diff.clipboard.vs.value.dialog.title");
    }

    @Nullable
    private static DiffContent createClipboardContent() {
      Transferable content = CopyPasteManager.getInstance().getContents();
      String text;
      try {
        text = (String) (content.getTransferData(DataFlavor.stringFlavor));
      } catch (Exception e) {
        return null;
      }
      return text != null ? new SimpleContent(text) : null;
    }
  }
}