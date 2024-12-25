// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

public class RenamePsiPackageProcessor extends RenamePsiElementProcessor {
  private static final Logger LOG = Logger.getInstance(RenamePsiPackageProcessor.class);

  @Override
  public boolean canProcessElement(final @NotNull PsiElement element) {
    return element instanceof PsiPackage;
  }

  @Override
  public @NotNull RenameDialog createRenameDialog(final @NotNull Project project, final @NotNull PsiElement element, PsiElement nameSuggestionContext, Editor editor) {

    return new RenameDialog(project, element, nameSuggestionContext, editor) {
      @Override
      protected void createNewNameComponent() {
        super.createNewNameComponent();
        final String qualifiedName = ((PsiPackage)element).getQualifiedName();
        final String packageName = StringUtil.getPackageName(qualifiedName);
        preselectExtension(packageName.isEmpty() ? 0 : packageName.length() + 1, qualifiedName.length());
      }

      @Override
      public String[] getSuggestedNames() {
        return new String[]{((PsiPackage)element).getQualifiedName()};
      }

      @Override
      public @NotNull String getNewName() {
        final PsiPackage psiPackage = (PsiPackage)element;
        final String oldName = psiPackage.getQualifiedName();
        final String newName = super.getNewName();
        if (!Comparing.strEqual(StringUtil.getPackageName(oldName), StringUtil.getPackageName(newName))) {
          return newName;
        }
        return StringUtil.getShortName(newName);
      }

      @Override
      protected void doAction() {
        final PsiPackage psiPackage = (PsiPackage)element;
        final String oldName = psiPackage.getQualifiedName();
        final String newName = super.getNewName();
        if (!Comparing.strEqual(StringUtil.getPackageName(oldName), StringUtil.getPackageName(newName))) {
          setToSearchInComments(psiPackage, isSearchInComments());
          if (isSearchForTextOccurrencesEnabled()) {
            setToSearchForTextOccurrences(psiPackage, isSearchInNonJavaFiles());
          }
          invokeRefactoring(createRenameMoveProcessor(newName, psiPackage, isSearchInComments(), isSearchInNonJavaFiles()));
        } else {
          super.doAction();
        }
      }
    };
  }

  public static MoveDirectoryWithClassesProcessor createRenameMoveProcessor(final String newName,
                                                                            final PsiPackage psiPackage,
                                                                            final boolean searchInComments,
                                                                            final boolean searchInNonJavaFiles) {
    final Project project = psiPackage.getProject();
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    final PsiDirectory[] directories = psiPackage.getDirectories();

    return new MoveDirectoryWithClassesProcessor(project, directories, null, searchInComments,
                                                 searchInNonJavaFiles, false, null) {
      @Override
      public @NotNull TargetDirectoryWrapper getTargetDirectory(final PsiDirectory dir) {
        final VirtualFile vFile = dir.getVirtualFile();
        final VirtualFile sourceRoot = index.getSourceRootForFile(vFile);
        LOG.assertTrue(sourceRoot != null, vFile.getPath());
        return new TargetDirectoryWrapper(dir.getManager().findDirectory(sourceRoot), newName.replaceAll("\\.", "\\/"));
      }

      @Override
      protected String getTargetName() {
        return newName;
      }

      @Override
      protected @NotNull String getCommandName() {
        return JavaBundle.message("rename.package.command.name");
      }
    };
  }

  @Override
  public void renameElement(final @NotNull PsiElement element,
                            final @NotNull String newName,
                            final UsageInfo @NotNull [] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    final PsiPackage psiPackage = (PsiPackage)element;
    final String shortName = StringUtil.getShortName(newName);
    psiPackage.handleQualifiedNameChange(PsiUtilCore.getQualifiedNameAfterRename(psiPackage.getQualifiedName(), shortName));
    RenameUtil.doRenameGenericNamedElement(element, shortName, usages, listener);
  }

  @Override
  public String getQualifiedNameAfterRename(final @NotNull PsiElement element, final @NotNull String newName, final boolean nonJava) {
    return getPackageQualifiedNameAfterRename((PsiPackage)element, newName, nonJava);
  }

  public static String getPackageQualifiedNameAfterRename(final PsiPackage element, final String newName, final boolean nonJava) {
    if (nonJava) {
      String qName = element.getQualifiedName();
      int index = qName.lastIndexOf('.');
      return index < 0 ? newName : qName.substring(0, index + 1) + newName;
    }
    else {
      return newName;
    }
  }

  @Override
  public void findExistingNameConflicts(@NotNull PsiElement element, @NotNull String newName,
                                        @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    final PsiPackage aPackage = (PsiPackage)element;
    final Project project = element.getProject();
    final String qualifiedNameAfterRename = getPackageQualifiedNameAfterRename(aPackage, newName, true);
    final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedNameAfterRename, GlobalSearchScope.allScope(project));
    if (psiClass != null) {
      conflicts.putValue(psiClass, JavaBundle.message("rename.package.class.already.exist.conflict", qualifiedNameAfterRename));
    }
  }

  @Override
  public void prepareRenaming(final @NotNull PsiElement element, final @NotNull String newName, final @NotNull Map<PsiElement, String> allRenames) {
    preparePackageRenaming((PsiPackage)element, newName, allRenames);
  }

  public static void preparePackageRenaming(PsiPackage psiPackage, final String newName, Map<PsiElement, String> allRenames) {
    final String newDirectoryName = StringUtil.getShortName(newName);
    final PsiDirectory[] directories = psiPackage.getDirectories();
    for (PsiDirectory directory : directories) {
      if (!JavaDirectoryService.getInstance().isSourceRoot(directory)) {
        allRenames.put(directory, newDirectoryName);
      }
    }
  }

  @Override
  public @Nullable Runnable getPostRenameCallback(final @NotNull PsiElement element, final @NotNull String newName, final @NotNull RefactoringElementListener listener) {
    final Project project = element.getProject();
    final PsiPackage psiPackage = (PsiPackage)element;
    final String newQualifiedName = PsiUtilCore.getQualifiedNameAfterRename(psiPackage.getQualifiedName(), newName);
    return () -> {
      final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(newQualifiedName);
      if (aPackage == null) {
        return; //rename failed e.g. when the dir is used by another app
      }
      listener.elementRenamed(aPackage);
    };
  }

  @Override
  public @Nullable @NonNls String getHelpID(final PsiElement element) {
    return HelpID.RENAME_PACKAGE;
  }

  @Override
  public boolean isToSearchInComments(final @NotNull PsiElement psiElement) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE;
  }

  @Override
  public void setToSearchInComments(final @NotNull PsiElement element, final boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE = enabled;
  }

  @Override
  public boolean isToSearchForTextOccurrences(final @NotNull PsiElement element) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE;
  }

  @Override
  public void setToSearchForTextOccurrences(final @NotNull PsiElement element, final boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE = enabled;
  }
}
