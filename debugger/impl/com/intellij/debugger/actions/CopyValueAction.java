package com.intellij.debugger.actions;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;

import java.awt.datatransfer.StringSelection;

/*
 * @author Jeka
 */
public class CopyValueAction extends BaseValueAction {
  protected void processText(final Project project, final String text) {
    CopyPasteManager.getInstance().setContents(new StringSelection(text));
  }
}
