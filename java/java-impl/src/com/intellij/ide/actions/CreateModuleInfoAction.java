// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.actions.AttributesDefaults;
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateActionBase;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.intellij.ide.fileTemplates.JavaTemplateUtil.INTERNAL_MODULE_INFO_TEMPLATE_NAME;
import static com.intellij.psi.PsiJavaModule.MODULE_INFO_CLASS;

public class CreateModuleInfoAction extends CreateFromTemplateActionBase {
  public CreateModuleInfoAction() {
    super(JavaBundle.messagePointer("action.create.new.module-info.title"), JavaBundle.messagePointer("action.create.new.module-info.description"), AllIcons.FileTypes.Java);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataContext ctx = e.getDataContext();
    IdeView view = LangDataKeys.IDE_VIEW.getData(ctx);
    PsiDirectory target = view != null && e.getProject() != null ? getTargetDirectory(ctx, view) : null;
    if (target == null || !PsiUtil.isLanguageLevel9OrHigher(target)) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(JavaModuleGraphUtil.findDescriptorByElement(target) == null);
    }
  }

  @Override
  protected @Nullable PsiDirectory getTargetDirectory(DataContext ctx, IdeView view) {
    PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 1) {
      PsiDirectory psiDir = directories[0];
      VirtualFile vDir = psiDir.getVirtualFile();
      ProjectFileIndex index = ProjectRootManager.getInstance(psiDir.getProject()).getFileIndex();
      if (index.isUnderSourceRootOfType(vDir, Set.of(JavaSourceRootType.SOURCE, JavaSourceRootType.TEST_SOURCE))) {
        VirtualFile root = index.getSourceRootForFile(vDir);
        if (root != null) {
          return psiDir.getManager().findDirectory(root);
        }
      }
    }

    return null;
  }

  @Override
  protected FileTemplate getTemplate(@NotNull Project project, @NotNull PsiDirectory dir) {
    return FileTemplateManager.getInstance(project).getInternalTemplate(INTERNAL_MODULE_INFO_TEMPLATE_NAME);
  }

  @Override
  protected AttributesDefaults getAttributesDefaults(@NotNull DataContext ctx) {
    return new AttributesDefaults(MODULE_INFO_CLASS).withFixedName(true);
  }

  @Override
  protected Map<String, String> getLiveTemplateDefaults(@NotNull DataContext ctx, @NotNull PsiFile file) {
    Module module = PlatformCoreDataKeys.MODULE.getData(ctx);
    return Collections.singletonMap("MODULE_NAME", module != null ? LightJavaModule.moduleName(module.getName()) : "module_name");
  }
}