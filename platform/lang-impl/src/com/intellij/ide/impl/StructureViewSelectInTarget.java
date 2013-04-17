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
  private final Project myProject;

  public StructureViewSelectInTarget(Project project) {
    myProject = project;
  }


  public String toString() {
    return IdeBundle.message("select.in.file.structure");
  }

  @Override
  public boolean canSelect(SelectInContext context) {
    return context.getFileEditorProvider() != null;
  }

  @Override
  public void selectIn(final SelectInContext context, final boolean requestFocus) {
    final FileEditor fileEditor = context.getFileEditorProvider().openFileEditor();

    ToolWindowManager windowManager=ToolWindowManager.getInstance(context.getProject());
    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        StructureViewFactoryEx.getInstanceEx(myProject).runWhenInitialized(new Runnable() {
          @Override
          public void run() {
            final StructureViewWrapper structureView = getStructureViewWrapper();
            structureView.selectCurrentElement(fileEditor, context.getVirtualFile(), requestFocus);
          }
        });
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
