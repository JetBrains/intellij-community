/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

/*
 * Class SetValueAction
 * @author Jeka
 */
public class CompareValueWithClipboardAction extends BaseValueAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.actions.CompareValueWithClipboardAction");
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

    private static DiffContent createClipboardContent() {
      Transferable content = CopyPasteManager.getInstance().getContents();
      String text = "";
      try {
        text = (String) (content.getTransferData(DataFlavor.stringFlavor));
      }
      catch (Exception e) {
        LOG.info(e);
      }
      return new SimpleContent(text);
    }
  }
}