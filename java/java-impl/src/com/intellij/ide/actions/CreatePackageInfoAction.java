// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.actions.AttributesDefaults;
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateActionBase;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import static com.intellij.ide.fileTemplates.JavaTemplateUtil.INTERNAL_PACKAGE_INFO_TEMPLATE_NAME;

/**
 * @author Bas Leijdekkers
 */
public class CreatePackageInfoAction extends CreateFromTemplateActionBase implements DumbAware {
  public CreatePackageInfoAction() {
    super(JavaBundle.messagePointer("action.create.new.package-info.title"), JavaBundle.messagePointer("action.create.new.package-info.description"), AllIcons.FileTypes.Java);
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
                                 JavaBundle.message("error.package.already.contains.package-info", aPackage.getQualifiedName()),
                                 IdeBundle.message("title.cannot.create.file"));
        return null;
      }
      else if (directory.findFile("package.html") != null) {
        if (Messages.showOkCancelDialog(CommonDataKeys.PROJECT.getData(dataContext),
                                        JavaBundle.message("error.package.already.contains.package.html", aPackage.getQualifiedName()),
                                        JavaBundle.message("error.package.html.found.title"),
                                        IdeBundle.message("button.create"), CommonBundle.getCancelButtonText(),
                                        Messages.getQuestionIcon()) != Messages.OK) {
          return null;
        }
      }

    }
    return super.getTargetDirectory(dataContext, view);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
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
    final PsiNameHelper nameHelper = PsiNameHelper.getInstance(project);
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
    return new AttributesDefaults(INTERNAL_PACKAGE_INFO_TEMPLATE_NAME).withFixedName(true);
  }

  @Override
  protected FileTemplate getTemplate(Project project, PsiDirectory dir) {
    return FileTemplateManager.getInstance(project).getInternalTemplate(INTERNAL_PACKAGE_INFO_TEMPLATE_NAME);
  }
}