// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.core.JavaPsiBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;


public class CreateCompactSourceFileAction extends CreateClassAction implements DumbAware {
  public CreateCompactSourceFileAction() {
    super(JavaBundle.messagePointer("action.create.new.compact.source.file"),
          JavaBundle.messagePointer("action.create.new.compact.source.file.description"),
          CreateCompactSourceFileAction::createIcon,
          JavaModuleSourceRootTypes.SOURCES
    );
  }

  private static @NotNull Icon createIcon() {
    IconManager iconManager = IconManager.getInstance();
    Icon icon = iconManager.createLayered(
      iconManager.getPlatformIcon(PlatformIcons.Class),
      iconManager.getPlatformIcon(PlatformIcons.FinalMark),
      iconManager.getPlatformIcon(PlatformIcons.RunnableMark)
    );
    return icon;
  }

  @VisibleForTesting
  @Override
  public boolean isAvailable(@NotNull DataContext dataContext) {
    if (!Registry.is("java.create.compact.source.file.separately")) return false;
    return super.isAvailable(dataContext) && isCompactSourceFilesAllowed(dataContext);
  }

  private static boolean isCompactSourceFilesAllowed(@NotNull DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null || project == null) return false;
    for (PsiDirectory directory : view.getDirectories()) {
      if (PsiUtil.isAvailable(JavaFeature.IMPLICIT_CLASSES, directory) &&
          isDefaultPackage(directory)
      ) {
        return true;
      }
    }
    return false;
  }

  private static boolean isDefaultPackage(@NotNull PsiDirectory directory) {
    String packageNameByDirectory = PackageIndex.getInstance(directory.getProject()).getPackageNameByDirectory(directory.getVirtualFile());
    return "".equals(packageNameByDirectory);
  }

  @Override
  protected void buildDialog(@NotNull Project project, @NotNull PsiDirectory directory,
                             @NotNull CreateFileFromTemplateDialog.Builder builder) {
    builder.setTitle(JavaBundle.message("action.create.new.compact.source.file"));
    builder.addKind(JavaPsiBundle.message("node.simple.source.file.tooltip"), createIcon(), JavaTemplateUtil.INTERNAL_SIMPLE_SOURCE_FILE);
    LanguageLevel level = PsiUtil.getLanguageLevel(directory);
    builder.setValidator(new CreateClassValidator(project, level));
    String defaultName = "Main";
    if (directory.findFile(defaultName + ".java") != null) {
      for (int i = 1; i < 100; i++) {
        if (directory.findFile(defaultName + i + ".java") == null) {
          defaultName += i;
          break;
        }
      }
    }
    builder.setDefaultText(defaultName);
  }
}