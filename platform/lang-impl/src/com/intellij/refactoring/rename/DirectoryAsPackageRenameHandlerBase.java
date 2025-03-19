// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.CommonBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.roots.ProjectFileIndex;
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

  protected abstract VirtualFile[] occursInPackagePrefixes(T aPackage);

  protected abstract boolean isIdentifier(String name, Project project);

  protected abstract String getQualifiedName(T aPackage);

  protected abstract @Nullable T getPackage(PsiDirectory psiDirectory);

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
  protected void doRename(PsiElement element, Project project, PsiElement nameSuggestionContext, Editor editor) {
    final PsiDirectory selected = (PsiDirectory)element;
    final T aPackage = getPackage(selected);
    final String qualifiedName = aPackage != null ? getQualifiedName(aPackage) : "";
    if (aPackage == null || qualifiedName.isEmpty()/*default package*/ || !isIdentifier(selected.getName(), project)) {
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
          renameDirs(project, nameSuggestionContext, editor, selected, aPackage, selected);
          return;
        }

        final @Nls StringBuffer message = new StringBuffer();
        RenameUtil.buildPackagePrefixChangedMessage(virtualFiles, message, qualifiedName);
        Module module = Objects.requireNonNull(ModuleUtilCore.findModuleForFile(selected.getVirtualFile(), project));
        buildMultipleDirectoriesInPackageMessage(message, getQualifiedName(aPackage), projectDirectories, selected);
        PsiDirectory[] moduleDirectories = aPackage.getDirectories(GlobalSearchScope.moduleScope(module));
        message.append(RefactoringBundle.message("directories.and.all.references.to.package.will.be.renamed"));
        List<String> options = new ArrayList<>();
        options.add(RefactoringBundle.message("rename.package.button.text"));
        if (projectDirectories.length > moduleDirectories.length) {
          if (moduleDirectories.length > 1) {
            options.add(RefactoringBundle.message("rename.source.root.button.text"));
          }
          options.add(RefactoringBundle.message("rename.directory.button.text"));
        }
        else {
          options.add(RefactoringBundle.message("rename.directory.button.text"));
        }
        options.add(CommonBundle.getCancelButtonText());

        int ret = Messages.showDialog(project, message.toString(), RefactoringBundle.message("dialog.title.rename.package.directories"),
                                      ArrayUtil.toStringArray(options), 0, Messages.getQuestionIcon());
        if (ret == 0) {
          if (directories.length > projectDirectories.length) {
            renameDirs(project, nameSuggestionContext, editor, selected, aPackage, projectDirectories);
          }
          else {
            PsiElementRenameHandler.rename(aPackage, project, nameSuggestionContext, editor);
          }
        }
        else if (ret == 1) {
          PsiDirectory[] dirsToRename = projectDirectories.length > moduleDirectories.length ? moduleDirectories : new PsiDirectory[]{selected};
          renameDirs(project, nameSuggestionContext, editor, selected, aPackage, dirsToRename);
        }
        else if (ret == 2 && options.size() > 3) {
          renameDirs(project, nameSuggestionContext, editor, selected, aPackage, selected);
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
                                                              @Nullable PsiDirectory selectedDirectory) {
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
    
    if (selectedDirectory != null) {
      Project project = selectedDirectory.getProject();
      Module selectedModule = Objects.requireNonNull(
        ModuleUtilCore.findModuleForFile(selectedDirectory.getVirtualFile(), project));
      source.sort((d1, d2) -> {
        if (d1 == d2) return 0;
        if (d1 == selectedDirectory) {
          return -1;
        }
        else if (d2 == selectedDirectory) {
          return 1;
        }
        Module m1 = ModuleUtilCore.findModuleForFile(d1.getVirtualFile(), selectedDirectory.getProject());
        Module m2 = ModuleUtilCore.findModuleForFile(d2.getVirtualFile(), selectedDirectory.getProject());
        if (m1 == selectedModule && m2 != selectedModule) {
          return -1;
        }
        else if (m1 != selectedModule && m2 == selectedModule) {
          return 1;
        }

        return presentableUrl(selectedDirectory, d1).compareToIgnoreCase(presentableUrl(selectedDirectory, d2));
      });
      //ensure current on the first place
      source.remove(selectedDirectory);
      source.add(0, selectedDirectory);
    }
    final Function<PsiDirectory, String> directoryPresentation = directory -> presentableUrl(selectedDirectory, directory);
    appendRoots(message, source, directoryPresentation);
    if (!generated.isEmpty()) {
      message.append("\n\n").append(RefactoringBundle.message("also.generated")).append("\n");
      appendRoots(message, generated, directoryPresentation);
    }
  }

  private static @NotNull @Nls String presentableUrl(@Nullable PsiDirectory currentVDirectory, PsiDirectory directory) {
    @Nls StringBuilder result = new StringBuilder();
    Module module = ProjectFileIndex.getInstance(directory.getProject()).getModuleForFile(directory.getVirtualFile());
    if (module != null) {
      result.append('[').append(module.getName()).append("] ");
    }
    result.append(SymbolPresentationUtil.getFilePathPresentation(directory));
    if (directory.equals(currentVDirectory)) {
      result.append(" (").append(RefactoringBundle.message("multiple.directories.correspond.to.package.current.marker")).append(")");
    }
    return result.toString();
  }

  private static void appendRoots(StringBuffer message, List<? extends PsiDirectory> source, Function<? super PsiDirectory, String> directoryPresentation) {
    int limit = Math.min(source.size(), 10);
    message.append(StringUtil.join(source.subList(0, limit), directoryPresentation, "\n"));
    if (limit < source.size()) {
      message.append("\n...\n");
    }
  }
}
