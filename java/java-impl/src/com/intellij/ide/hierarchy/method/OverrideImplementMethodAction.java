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
package com.intellij.ide.hierarchy.method;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.MethodHierarchyBrowserBase;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

abstract class OverrideImplementMethodAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.hierarchy.method.OverrideImplementMethodAction");

  public final void actionPerformed(final AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    final MethodHierarchyBrowser methodHierarchyBrowser = (MethodHierarchyBrowser)MethodHierarchyBrowserBase.DATA_KEY.getData(dataContext);
    if (methodHierarchyBrowser == null) return;
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;

    final String commandName = event.getPresentation().getText();
    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(project, () -> {

      try{
        final HierarchyNodeDescriptor[] selectedDescriptors = methodHierarchyBrowser.getSelectedDescriptors();
        if (selectedDescriptors.length > 0) {
          final List<VirtualFile> files = new ArrayList<>(selectedDescriptors.length);
          for (HierarchyNodeDescriptor selectedDescriptor : selectedDescriptors) {
            final PsiFile containingFile = ((MethodHierarchyNodeDescriptor)selectedDescriptor).getPsiClass().getContainingFile();
            if (containingFile != null) {
              final VirtualFile vFile = containingFile.getVirtualFile();
              if (vFile != null) {
                files.add(vFile);
              }
            }
          }
          final ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(VfsUtil.toVirtualFileArray(files));
          if (!status.hasReadonlyFiles()) {
            for (HierarchyNodeDescriptor selectedDescriptor : selectedDescriptors) {
              final PsiElement aClass = ((MethodHierarchyNodeDescriptor)selectedDescriptor).getPsiClass();
              if (aClass instanceof PsiClass) {
                OverrideImplementUtil.overrideOrImplement((PsiClass)aClass, methodHierarchyBrowser.getBaseMethod());
              }
            }
            ToolWindowManager.getInstance(project).activateEditorComponent();
          }
          else {
            ApplicationManager.getApplication().invokeLater(
              () -> Messages.showErrorDialog(project, status.getReadonlyFilesMessage(), commandName));
          }
        }
      }
      catch(IncorrectOperationException e){
        LOG.error(e);
      }
    }, commandName, null));
  }

  public final void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final DataContext dataContext = e.getDataContext();

    final MethodHierarchyBrowser methodHierarchyBrowser = (MethodHierarchyBrowser)MethodHierarchyBrowserBase.DATA_KEY.getData(dataContext);
    if (methodHierarchyBrowser == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    final HierarchyNodeDescriptor[] selectedDescriptors = methodHierarchyBrowser.getSelectedDescriptors();
    int toImplement = 0;
    int toOverride = 0;

    for (final HierarchyNodeDescriptor descriptor : selectedDescriptors) {
      if (canImplementOverride((MethodHierarchyNodeDescriptor)descriptor, methodHierarchyBrowser, true)) {
        if (toOverride > 0) {
          // no mixed actions allowed
          presentation.setEnabled(false);
          presentation.setVisible(false);
          return;
        }
        toImplement++;
      }
      else if (canImplementOverride((MethodHierarchyNodeDescriptor)descriptor, methodHierarchyBrowser, false)) {
        if (toImplement > 0) {
          // no mixed actions allowed
          presentation.setEnabled(false);
          presentation.setVisible(false);
          return;
        }
        toOverride++;
      }
      else {
        // no action is applicable to this node
        presentation.setEnabled(false);
        presentation.setVisible(false);
        return;
      }
    }

    presentation.setVisible(true);

    update(presentation, toImplement, toOverride);
  }

  protected abstract void update(Presentation presentation, int toImplement, int toOverride);

  private static boolean canImplementOverride(final MethodHierarchyNodeDescriptor descriptor, final MethodHierarchyBrowser methodHierarchyBrowser, final boolean toImplement) {
    final PsiElement psiElement = descriptor.getPsiClass();
    if (!(psiElement instanceof PsiClass)) return false;
    final PsiClass psiClass = (PsiClass)psiElement;
    if (psiClass instanceof PsiSyntheticClass) return false;
    final PsiMethod baseMethod = methodHierarchyBrowser.getBaseMethod();
    if (baseMethod == null) return false;
    final MethodSignature signature = baseMethod.getSignature(PsiSubstitutor.EMPTY);

    Collection<MethodSignature> allOriginalSignatures = toImplement
                                                        ? OverrideImplementUtil.getMethodSignaturesToImplement(psiClass)
                                                        : OverrideImplementUtil.getMethodSignaturesToOverride(psiClass);
    for (final MethodSignature originalSignature : allOriginalSignatures) {
      if (originalSignature.equals(signature)) {
        return true;
      }
    }

    return false;
  }
}
