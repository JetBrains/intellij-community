/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Properties;

import static com.intellij.ide.fileTemplates.JavaTemplateUtil.INTERNAL_MODULE_INFO_TEMPLATE_NAME;
import static com.intellij.psi.PsiJavaModule.MODULE_INFO_CLASS;
import static com.intellij.psi.PsiJavaModule.MODULE_INFO_FILE;

public class CreateModuleInfoAction extends CreateFromTemplateActionBase {
  public CreateModuleInfoAction() {
    super(IdeBundle.message("action.create.new.module-info.title"), IdeBundle.message("action.create.new.module-info.description"), AllIcons.FileTypes.Java);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataContext ctx = e.getDataContext();
    boolean available = Optional.ofNullable(LangDataKeys.IDE_VIEW.getData(ctx))
      .map(view -> getTargetDirectory(ctx, view))
      .filter(dir -> JavaDirectoryService.getInstance().isSourceRoot(dir) && PsiUtil.isLanguageLevel9OrHigher(dir))
      .map(ModuleUtilCore::findModuleForPsiElement)
      .map(module -> FilenameIndex.getVirtualFilesByName(module.getProject(), MODULE_INFO_FILE, module.getModuleScope(false)).isEmpty())
      .orElse(false);
    e.getPresentation().setEnabledAndVisible(available);
  }

  @Nullable
  @Override
  protected PsiDirectory getTargetDirectory(DataContext dataContext, IdeView view) {
    PsiDirectory[] directories = view.getDirectories();
    return directories.length == 1 ? directories[0] : null;
  }

  @Override
  protected FileTemplate getTemplate(@NotNull Project project, @NotNull PsiDirectory dir) {
    FileTemplate template = FileTemplateManager.getInstance(project).getInternalTemplate(INTERNAL_MODULE_INFO_TEMPLATE_NAME);
    template.setLiveTemplateEnabled(true);
    return template;
  }

  @Override
  public AttributesDefaults getAttributesDefaults(@NotNull DataContext ctx) {
    AttributesDefaults defaults = new AttributesDefaults(MODULE_INFO_CLASS).withFixedName(true);
    copyDefaultProperties(ctx, defaults);
    defaults.addPredefined("MODULE_NAME", "$module_name$");
    return defaults;
  }

  private static void copyDefaultProperties(DataContext ctx, AttributesDefaults defaults) {
    Project project = CommonDataKeys.PROJECT.getData(ctx);
    if (project != null) {
      Properties props = FileTemplateManager.getInstance(project).getDefaultProperties();
      props.stringPropertyNames().forEach(name -> defaults.addPredefined(name, props.getProperty(name)));
    }
  }
}