/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.ide.fileTemplates.actions.AttributesDefaults;
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateActionBase;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.generate.tostring.util.StringUtil;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

/**
 * @author Bas Leijdekkers
 */
public class CreatePackageInfoAction extends CreateFromTemplateActionBase implements DumbAware {

  protected CreatePackageInfoAction() {
    super("package-info.java", IdeBundle.message("action.create.new.package-info.description"), AllIcons.FileTypes.Java);
  }

  @Nullable
  @Override
  protected PsiDirectory getTargetDirectory(DataContext dataContext, IdeView view) {
    final PsiDirectory[] directories = view.getDirectories();
    for (PsiDirectory directory : directories) {
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
      if (aPackage == null) {
        continue;
      }
      if (directory.findFile(PsiPackage.PACKAGE_INFO_FILE) != null) {
        Messages.showErrorDialog(CommonDataKeys.PROJECT.getData(dataContext),
                                 "'package-info.java' already exists for package '" + aPackage.getQualifiedName() + '\'',
                                 IdeBundle.message("title.cannot.create.file"));
        return null;
      }
      else if (directory.findFile("package.html") != null) {
        if (Messages.showOkCancelDialog(CommonDataKeys.PROJECT.getData(dataContext),
                                    "Package '" + aPackage.getQualifiedName() + "' already has a 'package.html' file. Create 'package-info.java' anyway?",
                                    "Found 'package.html'", "Create", CommonBundle.message("button.cancel"),
                                    Messages.getQuestionIcon()) != Messages.OK) {
          return null;
        }
      }

    }
    return super.getTargetDirectory(dataContext, view);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isAvailable(e.getDataContext()));
  }

  private static boolean isAvailable(DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (project == null || view == null) {
      return false;
    }
    final PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 0) {
      return false;
    }
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final JavaDirectoryService directoryService = JavaDirectoryService.getInstance();
    final PsiNameHelper nameHelper = JavaPsiFacade.getInstance(project).getNameHelper();
    for (PsiDirectory directory : directories) {
      if (projectFileIndex.isUnderSourceRootOfType(directory.getVirtualFile(), JavaModuleSourceRootTypes.SOURCES) &&
          PsiUtil.isLanguageLevel5OrHigher(directory)) {
        final PsiPackage aPackage = directoryService.getPackage(directory);
        if (aPackage != null) {
          final String qualifiedName = aPackage.getQualifiedName();
          if (StringUtil.isEmpty(qualifiedName) || nameHelper.isQualifiedName(qualifiedName)) {
            return true;
          }
        }
      }

    }
    return false;
  }

  @Nullable
  @Override
  public AttributesDefaults getAttributesDefaults(DataContext dataContext) {
    return new AttributesDefaults("package-info").withFixedName(true);
  }

  @Nullable
  @Override
  protected AnAction getReplacedAction(FileTemplate selectedTemplate) {
    return null;
  }

  @Override
  protected FileTemplate getTemplate(Project project, PsiDirectory dir) {
    return FileTemplateManager.getInstance().getInternalTemplate(JavaTemplateUtil.INTERNAL_PACKAGE_INFO_TEMPLATE_NAME);
  }
}
