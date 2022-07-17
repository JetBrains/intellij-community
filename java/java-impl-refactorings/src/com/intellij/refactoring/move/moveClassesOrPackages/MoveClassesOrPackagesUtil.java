// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.lang.java.JavaFindUsagesProvider;
import com.intellij.model.ModelBranch;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.refactoring.util.CommonMoveClassesOrPackagesUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class MoveClassesOrPackagesUtil {
  private static final Logger LOG = Logger.getInstance(MoveClassesOrPackagesUtil.class);

  private MoveClassesOrPackagesUtil() {
  }

  /** @deprecated Use {@link #findUsages(PsiElement, SearchScope, boolean, boolean, String)} */
  @Deprecated
  public static UsageInfo[] findUsages(final PsiElement element,
                                       boolean searchInStringsAndComments,
                                       boolean searchInNonJavaFiles,
                                       final String newQName) {
    return findUsages(element, GlobalSearchScope.projectScope(element.getProject()),
                      searchInStringsAndComments, searchInNonJavaFiles, newQName);
  }

  public static UsageInfo @NotNull [] findUsages(@NotNull PsiElement element,
                                                 @NotNull SearchScope searchScope,
                                                 boolean searchInStringsAndComments,
                                                 boolean searchInNonJavaFiles,
                                                 String newQName) {
    ArrayList<UsageInfo> results = new ArrayList<>();
    Set<PsiReference> foundReferences = new HashSet<>();

    for (PsiReference reference : ReferencesSearch.search(element, searchScope, false)) {
      TextRange range = reference.getRangeInElement();
      if (foundReferences.contains(reference)) continue;
      results.add(new MoveRenameUsageInfo(reference.getElement(), reference, range.getStartOffset(), range.getEndOffset(), element, false));
      foundReferences.add(reference);
    }

    findNonCodeUsages(element, searchScope, searchInStringsAndComments, searchInNonJavaFiles, newQName, results);
    preprocessUsages(results);
    return results.toArray(UsageInfo.EMPTY_ARRAY);
  }

  private static void preprocessUsages(ArrayList<UsageInfo> results) {
    for (MoveClassHandler handler : MoveClassHandler.EP_NAME.getExtensions()) {
      handler.preprocessUsages(results);
    }
  }

  public static void findNonCodeUsages(@NotNull PsiElement element,
                                       @NotNull SearchScope searchScope,
                                       boolean searchInStringsAndComments,
                                       boolean searchInNonJavaFiles,
                                       String newQName,
                                       @NotNull Collection<? super UsageInfo> results) {
    final String stringToSearch = getStringToSearch(element);
    if (stringToSearch == null) return;
    TextOccurrencesUtil.findNonCodeUsages(element, searchScope, stringToSearch,
                                          searchInStringsAndComments, searchInNonJavaFiles, newQName, results);
  }

  private static String getStringToSearch(PsiElement element) {
    if (element instanceof PsiPackage) {
      return ((PsiPackage)element).getQualifiedName();
    }
    else if (element instanceof PsiClass) {
      return ((PsiClass)element).getQualifiedName();
    }
    else if (element instanceof PsiDirectory) {
      return getStringToSearch(JavaDirectoryService.getInstance().getPackage((PsiDirectory)element));
    }
    else if (element instanceof PsiClassOwner) {
      return ((PsiClassOwner)element).getName();
    }
    else {
      LOG.error("Unknown element: " + (element == null ? null : element.getClass().getName()));
      return null;
    }
  }

  // Does not process non-code usages!
  @NotNull
  static PsiPackage doMovePackage(@NotNull PsiPackage aPackage,
                                  @NotNull GlobalSearchScope scope,
                                  @NotNull MoveDestination moveDestination) throws IncorrectOperationException {
    final PackageWrapper targetPackage = moveDestination.getTargetPackage();

    final String newPrefix;
    if (targetPackage.getQualifiedName().isEmpty()) {
      newPrefix = "";
    }
    else {
      newPrefix = targetPackage.getQualifiedName() + ".";
    }

    final String newPackageQualifiedName = newPrefix + aPackage.getName();

    // do actual move
    PsiDirectory[] dirs = aPackage.getDirectories(scope);
    for (PsiDirectory dir : dirs) {
      final PsiDirectory targetDirectory = moveDestination.getTargetDirectory(dir);
      if (targetDirectory != null) {
        moveDirectoryRecursively(dir, targetDirectory);
      }
    }

    return findPackage(aPackage.getManager(), scope, newPackageQualifiedName);
  }

  @NotNull
  private static PsiPackageImpl findPackage(@NotNull PsiManager manager, @NotNull GlobalSearchScope scope, String qName) {
    return new PsiPackageImpl(manager, qName) {
      @Override
      public boolean isValid() {
        if (scope.getModelBranchesAffectingScope().isEmpty()) {
          // Already merged -- PsiPackage can live longer than the branch
          return super.isValid();
        }
        return !getProject().isDisposed() &&
               PackageIndex.getInstance(getProject()).getDirsByPackageName(qName, scope).findFirst() != null;
      }
    };
  }

  public static void moveDirectoryRecursively(PsiDirectory dir, PsiDirectory destination)
    throws IncorrectOperationException {
    if ( dir.getParentDirectory() == destination ) return;
    moveDirectoryRecursively(dir, destination, new HashSet<>());
  }

  private static void moveDirectoryRecursively(PsiDirectory dir, PsiDirectory destination, HashSet<? super VirtualFile> movedPaths) throws IncorrectOperationException {
    final VirtualFile destVFile = destination.getVirtualFile();
    final VirtualFile sourceVFile = dir.getVirtualFile();
    if (movedPaths.contains(sourceVFile)) return;
    String targetName = dir.getName();
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(dir);
    if (aPackage != null) {
      final String sourcePackageName = aPackage.getName();
      if (sourcePackageName != null && !sourcePackageName.equals(targetName)) {
        targetName = sourcePackageName;
      }
    }
    final PsiDirectory subdirectoryInDest;
    final boolean isSourceRoot = RefactoringUtil.isSourceRoot(dir);
    if (VfsUtilCore.isAncestor(sourceVFile, destVFile, false) || isSourceRoot) {
      PsiDirectory existingSubdir = destination.findSubdirectory(targetName);
      if (existingSubdir == null) {
        subdirectoryInDest = destination.createSubdirectory(targetName);
        movedPaths.add(subdirectoryInDest.getVirtualFile());
      }
      else {
        subdirectoryInDest = existingSubdir;
      }
    }
    else {
      subdirectoryInDest = destination.findSubdirectory(targetName);
    }

    if (subdirectoryInDest == null) {
      VirtualFile virtualFile = dir.getVirtualFile();
      MoveFilesOrDirectoriesUtil.doMoveDirectory(dir, destination);
      movedPaths.add(virtualFile);
    }
    else {
      final PsiFile[] files = dir.getFiles();
      for (PsiFile file : files) {
        try {
          subdirectoryInDest.checkAdd(file);
        }
        catch (IncorrectOperationException e) {
          continue;
        }
        MoveFilesOrDirectoriesUtil.doMoveFile(file, subdirectoryInDest);
      }

      final PsiDirectory[] subdirectories = dir.getSubdirectories();
      for (PsiDirectory subdirectory : subdirectories) {
        if (!subdirectory.equals(subdirectoryInDest)) {
          moveDirectoryRecursively(subdirectory, subdirectoryInDest, movedPaths);
        }
      }
      if (!isSourceRoot && dir.getFiles().length == 0 && dir.getSubdirectories().length == 0) {
        dir.delete();
      }
    }
  }

  public static void prepareMoveClass(PsiClass aClass) {
    for (MoveClassHandler handler : MoveClassHandler.EP_NAME.getExtensions()) {
      handler.prepareMove(aClass);
    }
  }

  public static void finishMoveClass(PsiClass aClass) {
    for (MoveClassHandler handler : MoveClassHandler.EP_NAME.getExtensions()) {
      handler.finishMoveClass(aClass);
    }
  }

  // Does not process non-code usages!
  public static PsiClass doMoveClass(PsiClass aClass, PsiDirectory moveDestination) throws IncorrectOperationException {
    return doMoveClass(aClass, moveDestination, true);
  }

  // Does not process non-code usages!
  public static PsiClass doMoveClass(PsiClass aClass, PsiDirectory moveDestination, boolean moveAllClassesInFile) throws IncorrectOperationException {
    PsiClass newClass;
    if (!moveAllClassesInFile) {
      for (MoveClassHandler handler : MoveClassHandler.EP_NAME.getExtensions()) {
        newClass = handler.doMoveClass(aClass, moveDestination);
        if (newClass != null) return newClass;
      }
    }

    PsiFile file = aClass.getContainingFile();
    Project project = moveDestination.getProject();
    VirtualFile dstDir = moveDestination.getVirtualFile();
    String pkgName = ProjectRootManager.getInstance(project).getFileIndex().getPackageNameByDirectory(dstDir);
    PsiPackage newPackage = pkgName == null ? null
                                            : findPackage(moveDestination.getManager(), moveDestination.getResolveScope(), pkgName);

    newClass = aClass;
    final PsiDirectory containingDirectory = file.getContainingDirectory();
    if (!Comparing.equal(dstDir, containingDirectory != null ? containingDirectory.getVirtualFile() : null)) {
      MoveFilesOrDirectoriesUtil.doMoveFile(file, moveDestination);

      if (ModelBranch.getPsiBranch(moveDestination) == null) {
        DumbService.getInstance(project).completeJustSubmittedTasks();
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        Document document = documentManager.getCachedDocument(file);
        if (document != null) {
          documentManager.commitDocument(document);
        }
      }

      file = moveDestination.findFile(file.getName());

    }

    if (newPackage != null && file instanceof PsiClassOwner && !FileTypeUtils.isInServerPageFile(file) &&
        !PsiUtil.isModuleFile(file)) {
      String qualifiedName = newPackage.getQualifiedName();
      if (!Comparing.strEqual(qualifiedName, ((PsiClassOwner)file).getPackageName()) &&
          (qualifiedName.isEmpty() || PsiNameHelper.getInstance(file.getProject()).isQualifiedName(qualifiedName))) {
        // Do not rely on class instance identity retention after setPackageName (Scala)
        String aClassName = aClass.getName();
        ((PsiClassOwner)file).setPackageName(qualifiedName);
        newClass = findClassByName((PsiClassOwner)file, aClassName);
        LOG.assertTrue(newClass != null, "name:" + aClassName + " file:" + file + " classes:" + Arrays.toString(((PsiClassOwner)file).getClasses()));
      }
    }
    return newClass;
  }

  @Nullable
  private static PsiClass findClassByName(PsiClassOwner file, String name) {
    PsiClass[] classes = file.getClasses();
    for (PsiClass aClass : classes) {
      if (name.equals(aClass.getName())) {
        return aClass;
      }
    }
    return null;
  }

  @NotNull
  public static String getPackageName(@NotNull PackageWrapper aPackage) {
    String name = aPackage.getQualifiedName();
    if (!name.isEmpty()) {
      return name;
    }
    return JavaFindUsagesProvider.getDefaultPackageName();
  }

  /**
   * @deprecated use CommonMoveClassesOrPackagesUtil.buildDirectoryList
   */
  @Deprecated
  public static void buildDirectoryList(@NotNull PackageWrapper aPackage,
                                        @NotNull List<? extends VirtualFile> contentSourceRoots,
                                        @NotNull LinkedHashSet<? super PsiDirectory> targetDirectories,
                                        @NotNull Map<PsiDirectory, String> relativePathsToCreate) {
    CommonMoveClassesOrPackagesUtil.buildDirectoryList(aPackage, contentSourceRoots, targetDirectories, relativePathsToCreate);
  }

  /**
   * @deprecated use CommonMoveClassesOrPackagesUtil.chooseDestinationPackage
   */
  @Deprecated
  @Nullable
  public static PsiDirectory chooseDestinationPackage(Project project, String packageName, @Nullable PsiDirectory baseDir) {
    return CommonMoveClassesOrPackagesUtil.chooseDestinationPackage(project, packageName, baseDir);
  }

  /**
   * @deprecated use CommonMoveClassesOrPackagesUtil.chooseSourceRoot
   */
  @Deprecated
  @Nullable
  public static VirtualFile chooseSourceRoot(@NotNull PackageWrapper targetPackage,
                                             @NotNull List<? extends VirtualFile> contentSourceRoots,
                                             @Nullable PsiDirectory initialDirectory) {
    return CommonMoveClassesOrPackagesUtil.chooseSourceRoot(targetPackage, contentSourceRoots, initialDirectory);
  }
}
