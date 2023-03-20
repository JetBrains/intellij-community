// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.structureView.StructureViewFactoryEx;
import com.intellij.ide.structureView.StructureViewWrapper;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.PlatformUtils;

public class StructureViewSelectInTarget implements SelectInTarget {
  public static final Key<StructureViewWrapper> CUSTOM_STRUCTURE_VIEW_KEY = Key.create("customStructureView");

  private final Project myProject;

  public StructureViewSelectInTarget(Project project) {
    myProject = project;

    if (PlatformUtils.isPyCharmEducational()) {
      throw ExtensionNotApplicableException.create();
    }
  }

  public String toString() {
    return IdeBundle.message("select.in.file.structure");
  }

  @Override
  public boolean canSelect(SelectInContext context) {
    return context.getFileEditorProvider() != null && !LightEdit.owns(context.getProject());
  }

  @Override
  public void selectIn(final SelectInContext context, final boolean requestFocus) {
    @SuppressWarnings("ConstantConditions")
    final FileEditor fileEditor = context.getFileEditorProvider().openFileEditor();

    StructureViewWrapper customStructureView = CUSTOM_STRUCTURE_VIEW_KEY.get(context.getVirtualFile());
    if (customStructureView != null) {
      customStructureView.selectCurrentElement(fileEditor, context.getVirtualFile(), requestFocus);
      return;
    }

    ToolWindowManager windowManager = ToolWindowManager.getInstance(context.getProject());
    final Runnable runnable = () -> StructureViewFactoryEx.getInstanceEx(myProject).runWhenInitialized(
      () -> getStructureViewWrapper().selectCurrentElement(fileEditor, context.getVirtualFile(), requestFocus));
    if (requestFocus) {
      ToolWindow window = windowManager.getToolWindow(getToolWindowId());
      // not all startup activities might have passed?
      if (window != null) {
        window.activate(runnable);
      }
    }
    else {
      runnable.run();
    }
  }

  private StructureViewWrapper getStructureViewWrapper() {
    return StructureViewFactoryEx.getInstanceEx(myProject).getStructureViewWrapper();
  }

  @Override
  public String getToolWindowId() {
    return ToolWindowId.STRUCTURE_VIEW;
  }

  @Override
  public String getMinorViewId() {
    return null;
  }

  @Override
  public float getWeight() {
    return StandardTargetWeights.STRUCTURE_WEIGHT;
  }
}