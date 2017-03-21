/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.rename;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author yole
 */
public class RenamePsiPackageProcessor extends RenamePsiElementProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenamePsiPackageProcessor");

  public boolean canProcessElement(@NotNull final PsiElement element) {
    return element instanceof PsiPackage;
  }

  @Override
  public RenameDialog2 createRenameDialog2(Project project, PsiElement element, PsiElement nameSuggestionContext, Editor editor) {
    RenameDialog2 d = super.createRenameDialog2(project, element, nameSuggestionContext, editor);
    final String qualifiedName = ((PsiPackage)element).getQualifiedName();
    final String packageName = StringUtil.getPackageName(qualifiedName);
    d.setInitialSelection(s -> TextRange.create(packageName.isEmpty() ? 0 : packageName.length() + 1, qualifiedName.length()));
    Consumer<PerformRenameRequest> superPerformRename = d.getPerformRename();
    Function<String, ValidationResult> superValidate = d.getValidate();
    final String oldName = ((PsiPackage)element).getQualifiedName();
    d.setValidate(s -> {
      if (Objects.equals(s, oldName))
        return new ValidationResult(false, null);
      return superValidate.apply(Comparing.strEqual(StringUtil.getPackageName(oldName), StringUtil.getPackageName(s)) ? StringUtil.getShortName(s) : s);});
    d.setPerformRename((request) -> {
      final PsiPackage psiPackage = (PsiPackage)element;
      if (Comparing.strEqual(StringUtil.getPackageName(oldName), StringUtil.getPackageName(request.getNewName()))) {
        superPerformRename.accept(new PerformRenameRequest(StringUtil.getShortName(request.getNewName()),
                                                           request.isPreview(),
                                                           request.getCallback(),
                                                           request.getDialog()));
      }
      else {
        RenameDialog2Kt.invokeRefactoring(createRenameMoveProcessor(request.getNewName(),
                                                                    psiPackage,
                                                                    d.getSearchInComments().getValue(),
                                                                    d.getSearchTextOccurrences().getValue()),
                                          request.isPreview(),
                                          request.getCallback());
      }
    });
    d.setSuggestedNames(Collections.singletonList(((PsiPackage)element).getQualifiedName()));

    return d;
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
      public TargetDirectoryWrapper getTargetDirectory(final PsiDirectory dir) {
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
      protected String getCommandName() {
        return "Rename package";
      }
    };
  }

  public void renameElement(final PsiElement element,
                            final String newName,
                            final UsageInfo[] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    final PsiPackage psiPackage = (PsiPackage)element;
    final String shortName = StringUtil.getShortName(newName);
    psiPackage.handleQualifiedNameChange(PsiUtilCore.getQualifiedNameAfterRename(psiPackage.getQualifiedName(), shortName));
    RenameUtil.doRenameGenericNamedElement(element, shortName, usages, listener);
  }

  public String getQualifiedNameAfterRename(final PsiElement element, final String newName, final boolean nonJava) {
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
  public void findExistingNameConflicts(PsiElement element, String newName, MultiMap<PsiElement,String> conflicts) {
    final PsiPackage aPackage = (PsiPackage)element;
    final Project project = element.getProject();
    final String qualifiedNameAfterRename = getPackageQualifiedNameAfterRename(aPackage, newName, true);
    final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedNameAfterRename, GlobalSearchScope.allScope(project));
    if (psiClass != null) {
      conflicts.putValue(psiClass, "Class with qualified name \'" + qualifiedNameAfterRename + "\'  already exist");
    }
  }

  public void prepareRenaming(final PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
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

  @Nullable
  public Runnable getPostRenameCallback(final PsiElement element, final String newName, final RefactoringElementListener listener) {
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

  @Nullable
  @NonNls
  public String getHelpID(final PsiElement element) {
    return HelpID.RENAME_PACKAGE;
  }

  public boolean isToSearchInComments(final PsiElement psiElement) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE;
  }

  public void setToSearchInComments(final PsiElement element, final boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE = enabled;
  }

  public boolean isToSearchForTextOccurrences(final PsiElement element) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE;
  }

  public void setToSearchForTextOccurrences(final PsiElement element, final boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE = enabled;
  }
}
