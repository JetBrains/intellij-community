// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
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
        PsiDirectory[] projectDirectories = aPackage.getDirectories(GlobalSearchScope.projectScope(project));

        if (virtualFiles.length == 0 && projectDirectories.length == 1) { //ignore library packages
          renameDirs(project, nameSuggestionContext, editor, psiDirectory, aPackage, psiDirectory);
          return;
        }

        final @Nls StringBuffer message = new StringBuffer();
        RenameUtil.buildPackagePrefixChangedMessage(virtualFiles, message, qualifiedName);
        Module module = Objects.requireNonNull(ModuleUtilCore.findModuleForFile(psiDirectory.getVirtualFile(), project));
        buildMultipleDirectoriesInPackageMessage(message, getQualifiedName(aPackage), projectDirectories, psiDirectory);
        PsiDirectory[] moduleDirectories = aPackage.getDirectories(GlobalSearchScope.moduleScope(module));
        message.append(RefactoringBundle.message("directories.and.all.references.to.package.will.be.renamed"));
        List<String> options = new ArrayList<>();
        options.add(RefactoringBundle.message("rename.package.button.text"));
        if (projectDirectories.length > moduleDirectories.length) {
          options.add(RefactoringBundle.message("rename.directory.button.text"));
        }
        if (moduleDirectories.length > 1) {
          options.add(RefactoringBundle.message("rename.source.root.button.text"));
        }
        options.add(CommonBundle.getCancelButtonText());

        int ret = Messages.showDialog(project, message.toString(), RefactoringBundle.message("dialog.title.rename.package.directories"),
                                      ArrayUtil.toStringArray(options), 0, Messages.getQuestionIcon());
        if (ret == 0) {
          if (directories.length > projectDirectories.length) {
            renameDirs(project, nameSuggestionContext, editor, psiDirectory, aPackage, projectDirectories);
          }
          else {
            PsiElementRenameHandler.rename(aPackage, project, nameSuggestionContext, editor);
          }
        }
        else if (ret == 1) {
          PsiDirectory[] dirsToRename = projectDirectories.length > moduleDirectories.length ? moduleDirectories : new PsiDirectory[]{psiDirectory};
          renameDirs(project, nameSuggestionContext, editor, psiDirectory, aPackage, dirsToRename);
        }
        else if (ret == 2 && options.size() > 3) {
          renameDirs(project, nameSuggestionContext, editor, psiDirectory, aPackage, psiDirectory);
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
                                                              PsiDirectory[] directories,
                                                              @Nullable PsiDirectory currentVDirectory) {
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
    
    if (currentVDirectory != null && source.indexOf(currentVDirectory) > 0) {
      //ensure current on the first place
      source.remove(currentVDirectory);
      source.add(0, currentVDirectory);
    }
    final Function<PsiDirectory, String> directoryPresentation = directory -> presentableUrl(currentVDirectory, directory);
    appendRoots(message, source, directoryPresentation);
    if (!generated.isEmpty()) {
      message.append("\n\n").append(RefactoringBundle.message("also.generated")).append("\n");
      appendRoots(message, generated, directoryPresentation);
    }
  }

  @NotNull
  private static @Nls String presentableUrl(@Nullable PsiDirectory currentVDirectory, PsiDirectory directory) {
    String presentableUrl = SymbolPresentationUtil.getFilePathPresentation(directory);
    if (directory.equals(currentVDirectory)) {
      return presentableUrl + " (" + RefactoringBundle.message("multiple.directories.correspond.to.package.current.marker") + ")";
    }
    return presentableUrl;
  }

  private static void appendRoots(StringBuffer message, List<PsiDirectory> source, Function<PsiDirectory, String> directoryPresentation) {
    int limit = Math.min(source.size(), 10);
    message.append(StringUtil.join(source.subList(0, limit), directoryPresentation, "\n"));
    if (limit < source.size()) {
      message.append("\n...\n");
    }
  }
}
