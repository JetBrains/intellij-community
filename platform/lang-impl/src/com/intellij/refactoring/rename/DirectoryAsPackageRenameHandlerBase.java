// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.file.PsiPackageBase;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public abstract class DirectoryAsPackageRenameHandlerBase<T extends PsiDirectoryContainer> extends DirectoryRenameHandlerBase {
  private static final Logger LOG = Logger.getInstance(DirectoryAsPackageRenameHandlerBase.class);

  protected abstract VirtualFile[] occursInPackagePrefixes(T aPackage);

  protected abstract boolean isIdentifier(String name, Project project);

  protected abstract String getQualifiedName(T aPackage);

  @Nullable
  protected abstract T getPackage(PsiDirectory psiDirectory);

  protected abstract BaseRefactoringProcessor createProcessor(final String newQName,
                                                              final Project project,
                                                              final PsiDirectory[] dirsToRename,
                                                              boolean searchInComments,
                                                              boolean searchInNonJavaFiles);

  @Override
  protected boolean isSuitableDirectory(PsiDirectory directory) {
    return getPackage(directory) != null;
  }

  @Override
  protected void doRename(PsiElement element, final Project project, PsiElement nameSuggestionContext, Editor editor) {
    final PsiDirectory psiDirectory = (PsiDirectory)element;
    final T aPackage = getPackage(psiDirectory);
    final String qualifiedName = aPackage != null ? getQualifiedName(aPackage) : "";
    if (aPackage == null || qualifiedName.length() == 0/*default package*/ ||
        !isIdentifier(psiDirectory.getName(), project)) {
      PsiElementRenameHandler.rename(element, project, nameSuggestionContext, editor);
    }
    else {
      PsiDirectory[] directories = aPackage.getDirectories();
      final VirtualFile[] virtualFiles = occursInPackagePrefixes(aPackage);
      if (virtualFiles.length == 0 && directories.length == 1) {
        PsiElementRenameHandler.rename(aPackage, project, nameSuggestionContext, editor);
      }
      else { // the directory corresponds to a package that has multiple associated directories
        final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        boolean inLib = false;
        for (PsiDirectory directory : directories) {
          inLib |= !projectFileIndex.isInContent(directory.getVirtualFile());
        }

        final PsiDirectory[] projectDirectories = aPackage.getDirectories(GlobalSearchScope.projectScope(project));
        if (inLib) {
          final Module module = ModuleUtilCore.findModuleForPsiElement(psiDirectory);
          LOG.assertTrue(module != null);
          PsiDirectory[] moduleDirs = null;
          if (nameSuggestionContext instanceof PsiPackageBase) {
            moduleDirs = aPackage.getDirectories(GlobalSearchScope.moduleScope(module));
            if (moduleDirs.length <= 1) {
              moduleDirs = null;
            }
          }
          if (projectDirectories.length > 0) {
            int ret = Messages
              .showYesNoCancelDialog(project, RefactoringBundle.message("rename.package.with.lib.multiple.message", aPackage.getName(), module.getName()),
                                     RefactoringBundle.message("warning.title"),
                                     RefactoringBundle.message("rename.package.button.text"),
                                     RefactoringBundle.message("rename.directory.button.text"), CommonBundle.getCancelButtonText(),
                                     Messages.getWarningIcon());
            if (ret == Messages.CANCEL) return;
            renameDirs(project, nameSuggestionContext, editor, psiDirectory, aPackage,
                       ret == Messages.YES ? projectDirectories : (moduleDirs == null ? new PsiDirectory[]{psiDirectory} : moduleDirs));
          }
          else {
            if (Messages.showOkCancelDialog(project, RefactoringBundle.message("rename.package.with.lib.message", aPackage.getName(), moduleDirs == null ? 1 : 2), RefactoringBundle.message("warning.title"),
                                            Messages.getWarningIcon()) == Messages.OK) {
              renameDirs(project, nameSuggestionContext, editor, psiDirectory, aPackage, psiDirectory);
            }
          }
        }
        else {
          final @Nls StringBuffer message = new StringBuffer();
          RenameUtil.buildPackagePrefixChangedMessage(virtualFiles, message, qualifiedName);
          Module module = Objects.requireNonNull(ModuleUtilCore.findModuleForFile(psiDirectory.getVirtualFile(), project));
          buildMultipleDirectoriesInPackageMessage(message, getQualifiedName(aPackage), directories);
          PsiDirectory[] moduleDirectories = aPackage.getDirectories(GlobalSearchScope.moduleScope(module));
          message.append(RefactoringBundle.message("directories.and.all.references.to.package.will.be.renamed"));
          List<String> options = new ArrayList<>();
          options.add(RefactoringBundle.message("rename.package.button.text"));
          if (directories.length > moduleDirectories.length) {
            options.add(RefactoringBundle.message("rename.directory.button.text"));
          }
          if (moduleDirectories.length > 1) {
            options.add(RefactoringBundle.message("rename.source.root.button.text"));
          }
          int ret = Messages.showDialog(project, message.toString(), RefactoringBundle.message("warning.title"),
                                        ArrayUtil.toStringArray(options), 0, Messages.getWarningIcon());
          if (ret == 0) {
            PsiElementRenameHandler.rename(aPackage, project, nameSuggestionContext, editor);
          }
          else if (ret == 1) {
            PsiDirectory[] dirsToRename = directories.length > moduleDirectories.length ? moduleDirectories : new PsiDirectory[]{psiDirectory};
            renameDirs(project, nameSuggestionContext, editor, psiDirectory, aPackage, dirsToRename);
          }
          else if (ret == 2) {
            renameDirs(project, nameSuggestionContext, editor, psiDirectory, aPackage, psiDirectory);
          }
        }
      }
    }
  }

  private void renameDirs(final Project project,
                          final PsiElement nameSuggestionContext,
                          final Editor editor,
                          final PsiDirectory contextDirectory,
                          final T aPackage,
                          final PsiDirectory... dirsToRename) {
    final RenameDialog dialog = new RenameDialog(project, contextDirectory, nameSuggestionContext, editor) {
      @Override
      protected void doAction() {
        String newQName = StringUtil.getQualifiedName(StringUtil.getPackageName(getQualifiedName(aPackage)), getNewName());
        BaseRefactoringProcessor moveProcessor = createProcessor(newQName, project, dirsToRename, isSearchInComments(),
                                                                 isSearchInNonJavaFiles());
        invokeRefactoring(moveProcessor);
      }
    };
    dialog.show();
  }

  public static void buildMultipleDirectoriesInPackageMessage(StringBuffer message,
                                                              String packageQname,
                                                              PsiDirectory[] directories) {
    message.append(RefactoringBundle.message("multiple.directories.correspond.to.package", packageQname));
    final List<PsiDirectory> generated = new ArrayList<>();
    final List<PsiDirectory> source = new ArrayList<>();
    for (PsiDirectory directory : directories) {
      final VirtualFile virtualFile = directory.getVirtualFile();
      if (GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(virtualFile, directory.getProject())) {
        generated.add(directory);
      } else {
        source.add(directory);
      }
    }
    final Function<PsiDirectory, String> directoryPresentation = directory -> directory.getVirtualFile().getPresentableUrl();
    message.append(StringUtil.join(source, directoryPresentation, "\n"));
    if (!generated.isEmpty()) {
      message.append("\n\n").append(RefactoringBundle.message("also.generated")).append("\n");
      message.append(StringUtil.join(generated, directoryPresentation, "\n"));
      
    }
  }
}
