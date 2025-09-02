// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.actions;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.GeneralModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class CreateTemplateInPackageAction<T extends PsiElement> extends CreateFromTemplateAction<T>
  implements NewFileActionWithCategory {

  private static final @NotNull Logger LOG = Logger.getInstance(CreateTemplateInPackageAction.class);

  public static final String JAVA_NEW_FILE_CATEGORY = "Java";

  private final @Nullable Set<? extends JpsModuleSourceRootType<?>> mySourceRootTypes;

  protected CreateTemplateInPackageAction(@Nullable Set<? extends JpsModuleSourceRootType<?>> sourceRootTypes) {
    mySourceRootTypes = sourceRootTypes;
  }

  protected CreateTemplateInPackageAction(String text, String description, Icon icon,
                                          Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    this(() -> text, () -> description, icon, rootTypes);
  }

  protected CreateTemplateInPackageAction(@NotNull Supplier<String> text,
                                          @NotNull Supplier<String> description,
                                          @Nullable Supplier<? extends @Nullable Icon> icon,
                                          @Nullable Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    super(text, description, icon);

    mySourceRootTypes = rootTypes;
  }

  protected CreateTemplateInPackageAction(@NotNull Supplier<String> dynamicText,
                                          @NotNull Supplier<String> dynamicDescription,
                                          @Nullable Icon icon,
                                          @Nullable Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    super(dynamicText, dynamicDescription, icon);
    mySourceRootTypes = rootTypes;
  }

  @Override
  public @NotNull String getCategory() {
    return JAVA_NEW_FILE_CATEGORY;
  }

  @Override
  protected @Nullable T createFile(String name, String templateName, PsiDirectory dir) {
    return checkOrCreate(name, dir, templateName);
  }

  protected abstract @Nullable PsiElement getNavigationElement(@NotNull T createdElement);

  @Override
  protected void postProcess(@NotNull T createdElement, String templateName, Map<String, String> customProperties) {
    super.postProcess(createdElement, templateName, customProperties);
    PsiElement element = getNavigationElement(createdElement);
    if (element != null) {
      Editor editor = PsiEditorUtil.findEditor(element);
      if (editor != null) {
        editor.getCaretModel().moveToOffset(element.getTextOffset());
      }
    }
  }

  @Override
  protected boolean isAvailable(@NotNull DataContext dataContext) {
    return isAvailable(dataContext, mySourceRootTypes, this::checkPackageExists);
  }

  @Override
  protected @NotNull PsiDirectory adjustDirectory(@NotNull PsiDirectory directory) {
    return adjustDirectory(directory, mySourceRootTypes);
  }

  public static @NotNull PsiDirectory adjustDirectory(@NotNull PsiDirectory directory, @Nullable Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    ProjectFileIndex index = ProjectRootManager.getInstance(directory.getProject()).getFileIndex();
    if (rootTypes != null && !index.isUnderSourceRootOfType(directory.getVirtualFile(), rootTypes)) {
      Module module = ModuleUtilCore.findModuleForPsiElement(directory);
      if (module == null) return directory;
      ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
      ContentEntry contentEntry =
        ContainerUtil.find(modifiableModel.getContentEntries(), entry -> entry.getFile() != null && VfsUtilCore.isAncestor(entry.getFile(), directory.getVirtualFile(), false));
      if (contentEntry == null ||
          !Objects.equals(contentEntry.getFile(), directory.getVirtualFile()) ||
          contentEntry.getSourceFolders().length > 0) {
        return directory;
      }
      contentEntry.addSourceFolder(directory.getVirtualFile(), false);
      WriteAction.run(() -> modifiableModel.commit());
    }
    return directory;
  }

  public static boolean isAvailable(DataContext dataContext, Set<? extends JpsModuleSourceRootType<?>> sourceRootTypes,
                                    Predicate<? super PsiDirectory> checkPackageExists) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (project == null || view == null || view.getDirectories().length == 0) {
      return false;
    }

    if (sourceRootTypes == null) {
      return true;
    }

    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    for (PsiDirectory dir : view.getDirectories()) {
      LOG.assertTrue(dir != null, view + " produced a null PsiDirectory");
      if (projectFileIndex.isUnderSourceRootOfType(dir.getVirtualFile(), sourceRootTypes) && checkPackageExists.test(dir)) {
        return true;
      }
      if (isInContentRoot(dir.getVirtualFile(), projectFileIndex)) {
        return true;
      }
    }

    return false;
  }

  public static boolean isInContentRoot(VirtualFile file, ProjectFileIndex index) {
    return file.equals(index.getContentRootForFile(file)) &&
           noSourceRootConfigured(file, index);
  }

  private static boolean noSourceRootConfigured(VirtualFile file, ProjectFileIndex index) {
    Module module = index.getModuleForFile(file);
    return module != null &&
           ModuleType.get(module) instanceof GeneralModuleType &&
           ModuleRootManager.getInstance(module).getSourceRoots().length == 0;
  }

  protected abstract boolean checkPackageExists(PsiDirectory directory);

  private @Nullable T checkOrCreate(String newName, PsiDirectory directory, String templateName) throws IncorrectOperationException {
    PsiDirectory dir = directory;
    String className = removeExtension(templateName, newName);

    if (className.contains(".")) {
      String[] names = className.split("\\.");

      for (int i = 0; i < names.length - 1; i++) {
        dir = CreateFileAction.findOrCreateSubdirectory(dir, names[i]);
      }

      className = names[names.length - 1];
    }

    DumbService service = DumbService.getInstance(dir.getProject());
    PsiDirectory finalDir = dir;
    String finalClassName = className;
    return service.computeWithAlternativeResolveEnabled(() ->
      doCreate(finalDir, finalClassName, templateName));
  }

  protected String removeExtension(String templateName, String className) {
    final String extension = StringUtil.getShortName(templateName);
    if (StringUtil.isNotEmpty(extension)) {
      className = StringUtil.trimEnd(className, "." + extension);
    }
    return className;
  }

  protected abstract @Nullable T doCreate(final PsiDirectory dir, final String className, String templateName) throws IncorrectOperationException;

}
