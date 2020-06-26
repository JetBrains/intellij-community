// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.lang.java.JavaFindUsagesProvider;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
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

  /** @deprecated Use {@link MoveClassesOrPackagesUtil#findNonCodeUsages(PsiElement, SearchScope, boolean, boolean, String, Collection)} */
  @Deprecated
  public static void findNonCodeUsages(boolean searchInStringsAndComments,
                                       boolean searchInNonJavaFiles,
                                       final PsiElement element,
                                       final String newQName,
                                       ArrayList<? super UsageInfo> results) {
    findNonCodeUsages(element, GlobalSearchScope.projectScope(element.getProject()),
                      searchInStringsAndComments, searchInNonJavaFiles, newQName, results);
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
      LOG.error("Unknown element type");
      return null;
    }
  }

  // Does not process non-code usages!
  public static PsiPackage doMovePackage(@NotNull PsiPackage aPackage,
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
    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(aPackage.getProject());
    PsiDirectory[] dirs = aPackage.getDirectories(projectScope);
    for (PsiDirectory dir : dirs) {
      final PsiDirectory targetDirectory = moveDestination.getTargetDirectory(dir);
      if (targetDirectory != null) {
        moveDirectoryRecursively(dir, targetDirectory);
      }
    }

    aPackage.handleQualifiedNameChange(newPackageQualifiedName);

    return JavaPsiFacade.getInstance(targetPackage.getManager().getProject()).findPackage(newPackageQualifiedName);
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
    final PsiPackage newPackage = JavaDirectoryService.getInstance().getPackage(moveDestination);

    newClass = aClass;
    final PsiDirectory containingDirectory = file.getContainingDirectory();
    if (!Comparing.equal(moveDestination.getVirtualFile(), containingDirectory != null ? containingDirectory.getVirtualFile() : null)) {
      LOG.assertTrue(file.getVirtualFile() != null, aClass);

      Project project = file.getProject();
      MoveFilesOrDirectoriesUtil.doMoveFile(file, moveDestination);

      DumbService.getInstance(project).completeJustSubmittedTasks();

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

  @Nullable
  public static PsiDirectory chooseDestinationPackage(Project project, String packageName, @Nullable PsiDirectory baseDir) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final PackageWrapper packageWrapper = new PackageWrapper(psiManager, packageName);
    final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
    PsiDirectory directory;

    PsiDirectory[] directories = aPackage != null ? aPackage.getDirectories() : null;
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFile baseDirVirtualFile = baseDir != null ? baseDir.getVirtualFile() : null;
    final boolean isBaseDirInTestSources = baseDirVirtualFile != null && fileIndex.isInTestSourceContent(baseDirVirtualFile);
    if (directories != null && directories.length == 1 && (baseDirVirtualFile == null ||
                                                           fileIndex.isInTestSourceContent(directories[0].getVirtualFile()) == isBaseDirInTestSources)) {
      directory = directories[0];
    }
    else {
      final List<VirtualFile> contentSourceRoots = JavaProjectRootsUtil.getSuitableDestinationSourceRoots(project);
      if (contentSourceRoots.size() == 1 && (baseDirVirtualFile == null || fileIndex.isInTestSourceContent(contentSourceRoots.get(0)) == isBaseDirInTestSources)) {
        directory = WriteAction
          .compute(() -> RefactoringUtil.createPackageDirectoryInSourceRoot(packageWrapper, contentSourceRoots.get(0)));
      }
      else {
        final VirtualFile sourceRootForFile = chooseSourceRoot(packageWrapper, contentSourceRoots, baseDir);
        if (sourceRootForFile == null) return null;
        directory = WriteAction.compute(
          () -> new AutocreatingSingleSourceRootMoveDestination(packageWrapper, sourceRootForFile).getTargetDirectory((PsiDirectory)null));
      }
    }
    return directory;
  }

  @Nullable
  public static VirtualFile chooseSourceRoot(@NotNull PackageWrapper targetPackage,
                                             @NotNull List<? extends VirtualFile> contentSourceRoots,
                                             @Nullable PsiDirectory initialDirectory) {
    Project project = targetPackage.getManager().getProject();
    //ensure that there would be no duplicates: e.g. when one content root is subfolder of another root (configured via excluded roots)
    LinkedHashSet<PsiDirectory> targetDirectories = new LinkedHashSet<>();
    Map<PsiDirectory, String> relativePathsToCreate = new HashMap<>();
    buildDirectoryList(targetPackage, contentSourceRoots, targetDirectories, relativePathsToCreate);

    PsiDirectory selectedDir = DirectoryChooserUtil.chooseDirectory(
      targetDirectories.toArray(PsiDirectory.EMPTY_ARRAY),
      initialDirectory,
      project,
      relativePathsToCreate);

    VirtualFile vDir = selectedDir == null ? null : selectedDir.getVirtualFile();
    return vDir == null ? null : ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(vDir);
  }

  public static void buildDirectoryList(@NotNull PackageWrapper aPackage,
                                        @NotNull List<? extends VirtualFile> contentSourceRoots,
                                        @NotNull LinkedHashSet<? super PsiDirectory> targetDirectories,
                                        @NotNull Map<PsiDirectory, String> relativePathsToCreate) {

    final PsiDirectory[] directories = aPackage.getDirectories();
    sourceRoots:
    for (VirtualFile root : contentSourceRoots) {
      if (!root.isDirectory()) continue;
      for (PsiDirectory directory : directories) {
        if (VfsUtilCore.isAncestor(root, directory.getVirtualFile(), false)) {
          targetDirectories.add(directory);
          continue sourceRoots;
        }
      }
      String qNameToCreate;
      try {
        qNameToCreate = RefactoringUtil.qNameToCreateInSourceRoot(aPackage, root);
      }
      catch (IncorrectOperationException e) {
        continue;
      }
      PsiDirectory currentDirectory = aPackage.getManager().findDirectory(root);
      if (currentDirectory == null) continue;
      final String[] shortNames = qNameToCreate.split("\\.");
      for (int j = 0; j < shortNames.length; j++) {
        String shortName = shortNames[j];
        final PsiDirectory subdirectory = currentDirectory.findSubdirectory(shortName);
        if (subdirectory == null) {
          targetDirectories.add(currentDirectory);
          final StringBuilder postfix = new StringBuilder();
          for (int k = j; k < shortNames.length; k++) {
            String name = shortNames[k];
            postfix.append(File.separatorChar);
            postfix.append(name);
          }
          relativePathsToCreate.put(currentDirectory, postfix.toString());
          continue sourceRoots;
        }
        else {
          currentDirectory = subdirectory;
        }
      }
    }
    LOG.assertTrue(targetDirectories.size() <= contentSourceRoots.size());
    LOG.assertTrue(relativePathsToCreate.size() <= contentSourceRoots.size());
  }
}
