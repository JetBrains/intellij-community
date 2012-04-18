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

package com.intellij.refactoring.copy;

import com.intellij.ide.TwoPaneIdeView;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.structureView.StructureViewFactoryEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.Content;

import javax.swing.*;

public class CopyHandler {
  private CopyHandler() {
  }

  public static boolean canCopy(PsiElement[] elements) {
    if (elements.length > 0) {
      final CopyHandlerDelegate[] copyHandlers = Extensions.getExtensions(CopyHandlerDelegate.EP_NAME);
      for(CopyHandlerDelegate delegate: copyHandlers) {
        if (delegate instanceof CopyHandlerDelegateBase ? ((CopyHandlerDelegateBase)delegate).canCopy(elements, true) : delegate.canCopy(elements)) return true;
      }
    }
    return false;
  }


  public static void doCopy(PsiElement[] elements, PsiDirectory defaultTargetDirectory) {
    if (elements.length == 0) return;
    for(CopyHandlerDelegate delegate: Extensions.getExtensions(CopyHandlerDelegate.EP_NAME)) {
      if (delegate.canCopy(elements)) {
        delegate.doCopy(elements, defaultTargetDirectory);
        break;
      }
    }
  }

  public static boolean canClone(PsiElement[] elements) {
    if (elements.length > 0) {
      final CopyHandlerDelegate[] copyHandlers = Extensions.getExtensions(CopyHandlerDelegate.EP_NAME);
      for (CopyHandlerDelegate delegate : copyHandlers) {
        if (delegate instanceof CopyHandlerDelegateBase ? ((CopyHandlerDelegateBase)delegate).canCopy(elements, true) : delegate.canCopy(elements)) {
          if (delegate instanceof CopyHandlerDelegateBase && ((CopyHandlerDelegateBase)delegate).forbidToClone(elements, true)){
            return false;
          }
          return true;
        }
      }
    }
    return false;
  }

  public static void doClone(PsiElement element) {
    PsiElement[] elements = new PsiElement[]{element};
    for(CopyHandlerDelegate delegate: Extensions.getExtensions(CopyHandlerDelegate.EP_NAME)) {
      if (delegate.canCopy(elements)) {
        if (delegate instanceof CopyHandlerDelegateBase && ((CopyHandlerDelegateBase)delegate).forbidToClone(elements, false)) {
          return;
        }
        delegate.doClone(element);
        break;
      }
    }
  }

  static void updateSelectionInActiveProjectView(PsiElement newElement, Project project, boolean selectInActivePanel) {
    String id = ToolWindowManager.getInstance(project).getActiveToolWindowId();
    if (id != null) {
      ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(id);
      Content selectedContent = window.getContentManager().getSelectedContent();
      if (selectedContent != null) {
        JComponent component = selectedContent.getComponent();
        if (component instanceof TwoPaneIdeView) {
          ((TwoPaneIdeView) component).selectElement(newElement, selectInActivePanel);
          return;
        }
      }
    }
    if (ToolWindowId.PROJECT_VIEW.equals(id)) {
      ProjectView.getInstance(project).selectPsiElement(newElement, true);
    }
    else if (ToolWindowId.STRUCTURE_VIEW.equals(id)) {
      VirtualFile virtualFile = newElement.getContainingFile().getVirtualFile();
      FileEditor editor = FileEditorManager.getInstance(newElement.getProject()).getSelectedEditor(virtualFile);
      StructureViewFactoryEx.getInstanceEx(project).getStructureViewWrapper().selectCurrentElement(editor, virtualFile, true);
    }
  }
}
