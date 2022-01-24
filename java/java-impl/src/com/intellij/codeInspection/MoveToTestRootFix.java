// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.ide.util.DirectoryChooser;
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

public class MoveToTestRootFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance(MoveToTestRootFix.class);

  public MoveToTestRootFix(PsiFile psiFile) {
    super(psiFile);
  }

  @Override
  public @IntentionName @NotNull String getText() {
    PsiFile containingFile = getStartElement().getContainingFile();
    if (containingFile instanceof PsiClassOwner) {
      PsiClass[] classes = ((PsiClassOwner)containingFile).getClasses();
      if (classes.length > 0) {
        return JavaBundle.message("intention.name.move.class.to.test.root", classes[0].getName());
      }
    }
    return getFamilyName();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.family.name.move.class.to.test.root");
  }

  public boolean isAvailable(PsiFile file) {
    if (file == null || !file.isValid() || !(file instanceof PsiJavaFile)) return false;
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(file);
    if (virtualFile == null) return false;
    Module module = ModuleUtilCore.findModuleForFile(virtualFile, file.getProject());
    if (module == null) return false;
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    ModuleFileIndex fileIndex = rootManager.getFileIndex();
    if (!fileIndex.isInSourceContent(virtualFile)) return false;
    return getTestRoots(rootManager).findFirst().isPresent();
  }

  @NotNull
  private static Stream<SourceFolder> getTestRoots(ModuleRootManager rootManager) {
    return Arrays.stream(rootManager.getContentEntries())
      .flatMap(entry -> Arrays.stream(entry.getSourceFolders()))
      .filter(SourceFolder::isTestSource);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    
    final PsiFile myFile = startElement.getContainingFile();

    if (!FileModificationService.getInstance().prepareFileForWrite(myFile)) return;
    Module module = ModuleUtilCore.findModuleForFile(myFile);
    Stream<SourceFolder> testRoots = getTestRoots(ModuleRootManager.getInstance(Objects.requireNonNull(module)));

    DirectoryChooser chooser = new DirectoryChooser(project);
    chooser.setTitle(JavaRefactoringBundle.message("select.source.root.chooser.title"));

    PsiManager manager = myFile.getManager();

    PsiDirectory[] directories = testRoots.map(ContentFolder::getFile).filter(Objects::nonNull)
      .map(vFile -> manager.findDirectory(vFile))
      .filter(Objects::nonNull).toArray(PsiDirectory[]::new);
    chooser.fillList(directories, null, project, "");
    if (!chooser.showAndGet()) {
      return;
    }
    PsiDirectory sourceRoot = chooser.getSelectedDirectory();
    if (sourceRoot == null) return;
    
    PsiPackage targetPackage = JavaDirectoryService.getInstance().getPackage(myFile.getContainingDirectory());
    
    PackageWrapper wrapper = PackageWrapper.create(targetPackage);

    PsiDirectory selectedDirectory =
      WriteAction.compute(() -> CommonJavaRefactoringUtil.createPackageDirectoryInSourceRoot(wrapper, sourceRoot.getVirtualFile()));

    try {
      String error;
      try {
        error = RefactoringMessageUtil.checkCanCreateFile(selectedDirectory, myFile.getName());
      }
      catch (IncorrectOperationException e) {
        error = e.getLocalizedMessage();
      }

      if (error != null) {
        Messages.showMessageDialog(project, error, CommonBundle.getErrorTitle(), Messages.getErrorIcon());
        return;
      }

      JavaSpecialRefactoringProvider.getInstance().moveClassesOrPackages(
        project,
        ((PsiJavaFile)myFile).getClasses(),
        new SingleSourceRootMoveDestination(wrapper, selectedDirectory), false,
        false,
        null);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
