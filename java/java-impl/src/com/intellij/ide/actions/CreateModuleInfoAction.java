/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.actions.AttributesDefaults;
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateActionBase;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static com.intellij.ide.fileTemplates.JavaTemplateUtil.INTERNAL_MODULE_INFO_TEMPLATE_NAME;
import static com.intellij.psi.PsiJavaModule.MODULE_INFO_CLASS;
import static com.intellij.psi.PsiJavaModule.MODULE_INFO_FILE;
import static java.util.Collections.singleton;

public class CreateModuleInfoAction extends CreateFromTemplateActionBase {
  public CreateModuleInfoAction() {
    super(IdeBundle.message("action.create.new.module-info.title"), IdeBundle.message("action.create.new.module-info.description"), AllIcons.FileTypes.Java);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataContext ctx = e.getDataContext();
    boolean available = Optional.ofNullable(LangDataKeys.IDE_VIEW.getData(ctx))
      .map(view -> getTargetDirectory(ctx, view))
      .filter(PsiUtil::isLanguageLevel9OrHigher)
      .map(ModuleUtilCore::findModuleForPsiElement)
      .map(module -> FilenameIndex.getVirtualFilesByName(module.getProject(), MODULE_INFO_FILE, module.getModuleScope()).isEmpty())
      .orElse(false);
    e.getPresentation().setEnabledAndVisible(available);
  }

  @Nullable
  @Override
  protected PsiDirectory getTargetDirectory(DataContext ctx, IdeView view) {
    PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 1) {
      PsiDirectory psiDir = directories[0];
      VirtualFile vDir = psiDir.getVirtualFile();
      ProjectFileIndex index = ProjectRootManager.getInstance(psiDir.getProject()).getFileIndex();
      if (vDir.equals(index.getSourceRootForFile(vDir)) &&
          index.isUnderSourceRootOfType(vDir, singleton(JavaSourceRootType.SOURCE))) {
        return psiDir;
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
    Module module = LangDataKeys.MODULE.getData(ctx);
    return Collections.singletonMap("MODULE_NAME", module != null ? LightJavaModule.moduleName(module.getName()) : "module_name");
  }
}