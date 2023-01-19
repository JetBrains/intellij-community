// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy.method;

import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

abstract class OverrideImplementMethodAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(OverrideImplementMethodAction.class);

  @Override
  public final void actionPerformed(@NotNull AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    MethodHierarchyBrowser methodHierarchyBrowser = getMethodHierarchyBrowser(event);
    if (methodHierarchyBrowser == null) return;
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;

    String commandName = event.getPresentation().getText();
    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(project, () -> {

      try{
        HierarchyNodeDescriptor[] selectedDescriptors = methodHierarchyBrowser.getSelectedDescriptors();
        if (selectedDescriptors.length > 0) {
          List<VirtualFile> files = new ArrayList<>(selectedDescriptors.length);
          for (HierarchyNodeDescriptor selectedDescriptor : selectedDescriptors) {
            PsiFile containingFile = ((MethodHierarchyNodeDescriptor)selectedDescriptor).getPsiClass().getContainingFile();
            if (containingFile != null) {
              VirtualFile vFile = containingFile.getVirtualFile();
              if (vFile != null) {
                files.add(vFile);
              }
            }
          }
          ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files);
          if (!status.hasReadonlyFiles()) {
            for (HierarchyNodeDescriptor selectedDescriptor : selectedDescriptors) {
              PsiElement aClass = ((MethodHierarchyNodeDescriptor)selectedDescriptor).getPsiClass();
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

  @Override
  public final void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    DataContext dataContext = e.getDataContext();

    MethodHierarchyBrowser methodHierarchyBrowser = getMethodHierarchyBrowser(e);
    if (methodHierarchyBrowser == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    Object[] data = PlatformCoreDataKeys.SELECTED_ITEMS.getData(dataContext);
    HierarchyNodeDescriptor[] selectedDescriptors = data instanceof HierarchyNodeDescriptor[] ? ((HierarchyNodeDescriptor[])data) 
                                                                                              : HierarchyNodeDescriptor.EMPTY_ARRAY;
    int toImplement = 0;
    int toOverride = 0;

    for (HierarchyNodeDescriptor descriptor : selectedDescriptors) {
      if (canImplementOverride((MethodHierarchyNodeDescriptor)descriptor, methodHierarchyBrowser, true)) {
        if (toOverride > 0) {
          // no mixed actions allowed
          presentation.setEnabledAndVisible(false);
          return;
        }
        toImplement++;
      }
      else if (canImplementOverride((MethodHierarchyNodeDescriptor)descriptor, methodHierarchyBrowser, false)) {
        if (toImplement > 0) {
          // no mixed actions allowed
          presentation.setEnabledAndVisible(false);
          return;
        }
        toOverride++;
      }
      else {
        // no action is applicable to this node
        presentation.setEnabledAndVisible(false);
        return;
      }
    }

    presentation.setVisible(true);

    update(presentation, toImplement, toOverride);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Nullable
  private static MethodHierarchyBrowser getMethodHierarchyBrowser(@NotNull AnActionEvent event) {
    return ObjectUtils.tryCast(event.getData(HierarchyBrowserBaseEx.HIERARCHY_BROWSER), MethodHierarchyBrowser.class);
  }

  protected abstract void update(Presentation presentation, int toImplement, int toOverride);

  private static boolean canImplementOverride(MethodHierarchyNodeDescriptor descriptor, MethodHierarchyBrowser methodHierarchyBrowser, boolean toImplement) {
    PsiElement psiElement = descriptor.getPsiClass();
    if (!(psiElement instanceof PsiClass)) return false;
    PsiClass psiClass = (PsiClass)psiElement;
    if (psiClass instanceof PsiSyntheticClass) return false;
    PsiMethod baseMethod = methodHierarchyBrowser.getBaseMethod();
    if (baseMethod == null) return false;
    MethodSignature signature = baseMethod.getSignature(PsiSubstitutor.EMPTY);

    Collection<MethodSignature> allOriginalSignatures = toImplement
                                                        ? OverrideImplementExploreUtil.getMethodSignaturesToImplement(psiClass)
                                                        : OverrideImplementExploreUtil.getMethodSignaturesToOverride(psiClass);
    return allOriginalSignatures.contains(signature);
  }
}
