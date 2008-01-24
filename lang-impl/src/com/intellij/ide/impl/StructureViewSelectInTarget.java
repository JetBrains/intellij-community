package com.intellij.ide.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.structureView.StructureViewFactoryEx;
import com.intellij.ide.structureView.StructureViewWrapper;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;

public class StructureViewSelectInTarget implements SelectInTarget {
  private Project myProject;

  public StructureViewSelectInTarget(Project project) {
    myProject = project;
  }


  public String toString() {
    return IdeBundle.message("select.in.file.structure");
  }

  public boolean canSelect(SelectInContext context) {
    return context.getFileEditorProvider() != null;
  }

  public void selectIn(final SelectInContext context, final boolean requestFocus) {
    final FileEditor fileEditor = context.getFileEditorProvider().openFileEditor();

    final StructureViewWrapper structureView = getStructureViewWrapper();
    ToolWindowManager windowManager=ToolWindowManager.getInstance(context.getProject());
    final Runnable runnable = new Runnable() {
      public void run() {
        structureView.selectCurrentElement(fileEditor,requestFocus);
      }
    };
    if (requestFocus) {
      windowManager.getToolWindow(ToolWindowId.STRUCTURE_VIEW).activate(runnable);
    }
    else {
      runnable.run();
    }

  }

  private StructureViewWrapper getStructureViewWrapper() {
    return StructureViewFactoryEx.getInstanceEx(myProject).getStructureViewWrapper();
  }

  public String getToolWindowId() {
    return ToolWindowId.STRUCTURE_VIEW;
  }

  public String getMinorViewId() {
    return null;
  }

  public float getWeight() {
    return StandardTargetWeights.STRUCTURE_WEIGHT;
  }

}
