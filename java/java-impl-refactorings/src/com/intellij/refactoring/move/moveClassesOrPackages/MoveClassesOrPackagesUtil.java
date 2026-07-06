// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.java.JavaFindUsagesProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.refactoring.util.CommonMoveClassesOrPackagesUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.refactoring.util.classRefs.ClassInstanceScanner;
import com.intellij.refactoring.util.classRefs.ClassReferenceScanner;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

@ApiStatus.Internal
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
    List<UsageInfo> results = Collections.synchronizedList(new ArrayList<>());
    Set<PsiReference> foundReferences = new HashSet<>();

    for (PsiReference reference : ReferencesSearch.search(element, searchScope, false).asIterable()) {
      TextRange range = reference.getRangeInElement();
      if (foundReferences.contains(reference)) continue;
      results.add(new MoveRenameUsageInfo(reference.getElement(), reference, range.getStartOffset(), range.getEndOffset(), element, false));
      foundReferences.add(reference);
    }

    findNonCodeUsages(element, searchScope, searchInStringsAndComments, searchInNonJavaFiles, newQName, results);
    preprocessUsages(results);
    return results.toArray(UsageInfo.EMPTY_ARRAY);
  }

  private static void preprocessUsages(@NotNull List<UsageInfo> results) {
    for (MoveClassHandler handler : MoveClassHandler.EP_NAME.getExtensions()) {
      handler.preprocessUsages(results);
    }
  }

  /**
   * @param results must be thread-safe
   */
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

  private static String getStringToSearch(@Nullable PsiElement element) {
    return switch (element) {
      case PsiPackage aPackage -> aPackage.getQualifiedName();
      case PsiClass aClass -> aClass.getQualifiedName();
      case PsiDirectory directory -> getStringToSearch(JavaDirectoryService.getInstance().getPackage(directory));
      case PsiClassOwner owner -> owner.getName();
      case null, default -> throw new IllegalArgumentException("Unknown element: " + (element == null ? null : element.getClass().getName()));
    };
  }

  // Does not process non-code usages!
  static @NotNull PsiPackage doMovePackage(@NotNull PsiPackage aPackage,
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

    return new PsiPackageImpl(aPackage.getManager(), newPackageQualifiedName);
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
  public static @NotNull PsiClass doMoveClass(@NotNull PsiClass aClass, @NotNull PsiDirectory moveDestination, boolean moveAllClassesInFile) throws IncorrectOperationException {
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
    PsiPackage newPackage = JavaDirectoryService.getInstance().getPackage(moveDestination);

    newClass = aClass;
    final PsiDirectory containingDirectory = file.getContainingDirectory();
    if (!Comparing.equal(dstDir, containingDirectory != null ? containingDirectory.getVirtualFile() : null)) {
      MoveFilesOrDirectoriesUtil.doMoveFile(file, moveDestination);

      DumbService.getInstance(project).completeJustSubmittedTasks();
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      Document document = documentManager.getCachedDocument(file);
      if (document != null) {
        documentManager.commitDocument(document);
      }

      file = MovedFileProvider.getInstance().getUpdatedFile(moveDestination, file);

    }

    if (newPackage != null && file instanceof PsiClassOwner && !FileTypeUtils.isInServerPageFile(file) &&
        !PsiUtil.isModuleFile(file)) {
      String qualifiedName = newPackage.getQualifiedName();
      if (!Comparing.strEqual(qualifiedName, ((PsiClassOwner)file).getPackageName()) &&
          (qualifiedName.isEmpty() || PsiNameHelper.getInstance(file.getProject()).isQualifiedName(qualifiedName))) {
        // Do not rely on class instance identity retention after setPackageName (Scala)
        String aClassName = aClass.getName();
        if (!(aClass instanceof PsiImplicitClass)) {
          ((PsiClassOwner)file).setPackageName(qualifiedName);
          newClass = findClassByName((PsiClassOwner)file, aClassName);
        }
        LOG.assertTrue(newClass != null, "name:" + aClassName + " file:" + file + " classes:" + Arrays.toString(((PsiClassOwner)file).getClasses()));
      }
    }
    return newClass;
  }

  private static @Nullable PsiClass findClassByName(PsiClassOwner file, String name) {
    PsiClass[] classes = file.getClasses();
    for (PsiClass aClass : classes) {
      if (name.equals(aClass.getName())) {
        return aClass;
      }
    }
    return null;
  }

  /**
   * Result of {@link #moveElements}.
   *
   * @param movedElementPointers    pointers to the moved elements.
   * @param oldToNewElementsMapping mapping from old elements (and old directories of moved packages) to their new counterparts.
   */
  public record MoveElementsResult(@NotNull List<@NotNull SmartPsiElementPointer<?>> movedElementPointers,
                                   @NotNull Map<PsiElement, PsiElement> oldToNewElementsMapping) {
    /**
     * Resolves each of the {@link #movedElementPointers()} to its current {@link PsiElement}.
     */
    public @NotNull List<@NotNull PsiElement> movedElements() {
      return ContainerUtil.mapNotNull(movedElementPointers, SmartPsiElementPointer::getElement);
    }
  }

  /**
   * Performs the actual move of {@code elementsToMove} to {@code moveDestination}.
   * <p>
   * Does not modify the input {@code elementsToMove}; the moved elements and their pointers are returned in
   * the resulting {@link MoveElementsResult}.
   */
  public static @NotNull MoveElementsResult moveElements(@NotNull Project project,
                                                         @NotNull MoveDestination moveDestination,
                                                         PsiElement @NotNull [] elementsToMove) {
    Map<PsiClass, Boolean> allClasses = new HashMap<>();
    for (PsiElement element : elementsToMove) {
      if (element instanceof PsiClass psiClass) {
        if (allClasses.containsKey(psiClass)) {
          continue;
        }
        for (MoveAllClassesInFileHandler fileHandler : MoveAllClassesInFileHandler.EP_NAME.getExtensionList()) {
          fileHandler.processMoveAllClassesInFile(allClasses, psiClass, elementsToMove);
        }
      }
    }

    for (PsiElement element : elementsToMove) {
      if (element instanceof PsiClass) {
        prepareMoveClass((PsiClass)element);
      }
    }

    final Map<PsiElement, PsiElement> oldToNewElementsMapping = new HashMap<>();
    List<SmartPsiElementPointer<?>> movedElementPointers = new ArrayList<>(elementsToMove.length);

    for (int idx = 0; idx < elementsToMove.length; idx++) {
      PsiElement element = elementsToMove[idx];
      if (element instanceof PsiPackage) {
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        PsiDirectory[] directories = ((PsiPackage)element).getDirectories(scope);
        PsiPackage newElement = doMovePackage((PsiPackage)element, scope, moveDestination);
        oldToNewElementsMapping.put(element, newElement);
        int i = 0;
        PsiDirectory[] newDirectories = newElement.getDirectories(scope);
        if (newDirectories.length == 1) {//everything is moved in one directory
          for (PsiDirectory directory : directories) {
            oldToNewElementsMapping.put(directory, newDirectories[0]);
          }
        } else {
          for (PsiDirectory directory : directories) {
            if (moveDestination.verify(directory) != null) {
              //e.g. directory is excluded so there is no source root for it, hence target directory would be missed from newDirectories
              continue;
            }

            oldToNewElementsMapping.put(directory, newDirectories[i++]);
          }
        }
        element = newElement;
      }
      else if (element instanceof PsiClass psiClass) {
        final PsiClass newElement = doMoveClass(psiClass, moveDestination.getTargetDirectory(element.getContainingFile()), allClasses.get(psiClass));
        oldToNewElementsMapping.put(element, newElement);
        element = newElement;
      }
      else if (element instanceof PsiClassOwner) {
        PsiDirectory directory = moveDestination.getTargetDirectory(element.getContainingFile());
        MoveFilesOrDirectoriesUtil.doMoveFile((PsiClassOwner)element, directory);
        PsiFile newElement = directory.findFile(((PsiClassOwner)element).getName());
        LOG.assertTrue(newElement != null);

        DumbService.getInstance(project).completeJustSubmittedTasks();

        final PsiPackage newPackage = JavaDirectoryService.getInstance().getPackage(directory);
        if (newPackage != null) {
          String qualifiedName = newPackage.getQualifiedName();
          if (!Comparing.strEqual(qualifiedName, ((PsiClassOwner)newElement).getPackageName()) &&
              (qualifiedName.isEmpty() || PsiNameHelper.getInstance(project).isQualifiedName(qualifiedName))) {
            ((PsiClassOwner)newElement).setPackageName(qualifiedName);
          }
        }
        oldToNewElementsMapping.put(element, newElement);
        element = newElement;
      }
      else {
        LOG.error("Unexpected element to move: " + element);
      }
      movedElementPointers.add(SmartPointerManager.createPointer(element));
    }

    DumbService.getInstance(project).completeJustSubmittedTasks();

    return new MoveElementsResult(movedElementPointers, oldToNewElementsMapping);
  }

  /**
   * Notifies {@code listeners} that the elements were moved.
   *
   * @param listeners            listeners to notify, one per index of {@code originalElements}, or {@code null} to skip notification.
   * @param movedElementPointers pointers to the moved elements, in the same order as {@code originalElements}.
   * @param originalElements     the elements before the move, in the same order as {@code movedElementPointers}.
   */
  public static void afterMovement(@NotNull List<RefactoringElementListener> listeners,
                                   @NotNull List<? extends @Nullable SmartPsiElementPointer<?>> movedElementPointers,
                                   PsiElement @NotNull [] originalElements) {
    for (int i = 0; i < movedElementPointers.size(); i++) {
      PsiElement element = getOriginalPsi(movedElementPointers.get(i));
      if (element != null) {
        if (originalElements[i] instanceof PsiPackage) {
          ((PsiPackage)originalElements[i]).handleQualifiedNameChange(((PsiPackage)element).getQualifiedName());
        }
        listeners.get(i).elementMoved(element);
      }
    }
  }

  private static @Nullable PsiElement getOriginalPsi(@Nullable SmartPsiElementPointer<?> pointer) {
    return pointer == null ? null : pointer.getElement();
  }

  public static @NotNull String getPackageName(@NotNull PackageWrapper aPackage) {
    String name = aPackage.getQualifiedName();
    if (!name.isEmpty()) {
      return name;
    }
    return JavaFindUsagesProvider.getDefaultPackageName();
  }

  /**
   * @deprecated use CommonMoveClassesOrPackagesUtil.chooseSourceRoot
   */
  @Deprecated
  public static @Nullable VirtualFile chooseSourceRoot(@NotNull PackageWrapper targetPackage,
                                             @NotNull List<? extends VirtualFile> contentSourceRoots,
                                             @Nullable PsiDirectory initialDirectory) {
    return CommonMoveClassesOrPackagesUtil.chooseSourceRoot(targetPackage, contentSourceRoots, initialDirectory);
  }

  public static PsiElement @NotNull [] collectElementsToMove(PsiElement @NotNull [] elements) {
    final Set<PsiElement> toMove = new LinkedHashSet<>();
    for (PsiElement element : elements) {
      PsiUtilCore.ensureValid(element);
      if (element instanceof PsiClassOwner) {
        PsiClass[] classes = ((PsiClassOwner)element).getClasses();
        if (classes.length > 0) {
          for (PsiClass aClass : classes) {
            PsiUtilCore.ensureValid(aClass);
            toMove.add(aClass);
          }
        }
        else {
          toMove.add(element);
        }
      }
      else {
        toMove.add(element);
      }
    }
    PsiElement[] elementsToMove = PsiUtilCore.toPsiElementArray(toMove);
    Arrays.sort(elementsToMove, (o1, o2) -> {
      if (o1 instanceof PsiClass && o2 instanceof PsiClass) {
        final PsiFile containingFile = o1.getContainingFile();
        if (Comparing.equal(containingFile, o2.getContainingFile())) {
          final VirtualFile virtualFile = containingFile.getVirtualFile();
          if (virtualFile != null) {
            final String fileName = virtualFile.getNameWithoutExtension();
            if (Comparing.strEqual(fileName, ((PsiClass)o1).getName())) return -1;
            if (Comparing.strEqual(fileName, ((PsiClass)o2).getName())) return 1;
          }
        }
      }
      return 0;
    });
    return elementsToMove;
  }

  /**
   * Searches for all usages of the elements that will be moved.
   * @param elementsToMove elements for which usages are searched.
   * @param targetPackage package to which the elements will be moved.
   */
  public static @NotNull UsagesContext findUsagesInElements(PsiElement @NotNull [] elementsToMove,
                                                                                      @NotNull SearchScope refactoringScope,
                                                                                      boolean searchInComments,
                                                                                      boolean searchInNonJavaFiles,
                                                                                      @NotNull PackageWrapper targetPackage) {
    final List<UsageInfo> allUsages = new ArrayList<>();
    final List<UsageInfo> usagesToSkip = new ArrayList<>();

    for (PsiElement element : elementsToMove) {
      String newName = getNewQName(element, targetPackage);
      UsageInfo[] usages = findUsages(
        element, refactoringScope, searchInComments, searchInNonJavaFiles, newName);
      final ArrayList<UsageInfo> infos = new ArrayList<>(Arrays.asList(usages));
      allUsages.addAll(infos);
      if (Comparing.strEqual(newName, getOldQName(element))) {
        usagesToSkip.addAll(infos);
      }

      if (element instanceof PsiPackage) {
        for (PsiDirectory directory : ((PsiPackage)element).getDirectories()) {
          UsageInfo[] dirUsages = findUsages(
            directory, refactoringScope, searchInComments, searchInNonJavaFiles, newName);
          allUsages.addAll(new ArrayList<>(Arrays.asList(dirUsages)));
        }
      }
    }
    return new UsagesContext(allUsages, usagesToSkip);
  }

  public static void collectConflicts(@NotNull List<UsageInfo> allUsages,
                                      @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts,
                                      PsiElement @NotNull [] elementsToMove,
                                      @NotNull PackageWrapper targetPackage,
                                      @NotNull MoveDestination moveDestination,
                                      @NotNull ModuleInfoUsageDetector moduleInfoUsageDetector) {
    detectConflicts(allUsages.toArray(UsageInfo.EMPTY_ARRAY), conflicts, elementsToMove, targetPackage, moveDestination);
    moduleInfoUsageDetector.detectModuleStatementsUsed(allUsages, conflicts);
  }

  public static UsageInfo @NotNull [] extractAffectedUsages(@NotNull List<UsageInfo> allUsages,
                                                            @NotNull List<UsageInfo> usagesToSkip) {
    List<UsageInfo> affectedUsages = new ArrayList<>(allUsages);
    affectedUsages.removeAll(usagesToSkip);
    return UsageViewUtil.removeDuplicatedUsages(affectedUsages.toArray(UsageInfo.EMPTY_ARRAY));
  }

  public static @NotNull RefactoringEventData createBeforeData(PsiElement @NotNull [] elementsToMove) {
    RefactoringEventData data = new RefactoringEventData();
    data.addElements(elementsToMove);
    return data;
  }

  static void detectConflicts(UsageInfo[] usageInfos,
                              MultiMap<PsiElement, @DialogMessage String> conflicts,
                              PsiElement @NotNull[] elementsToMove,
                              @NotNull PackageWrapper targetPackage,
                              @NotNull MoveDestination moveDestination) {
    moveDestination.analyzeModuleConflicts(Arrays.asList(elementsToMove), conflicts, usageInfos);
    detectPackageLocalsMoved(usageInfos, conflicts, targetPackage, elementsToMove);
    detectPackageLocalsUsed(conflicts, elementsToMove, targetPackage);
    detectMoveToDefaultPackage(usageInfos, conflicts, targetPackage);
  }

  private static @NotNull String getNewQName(@NotNull PsiElement element, @NotNull PackageWrapper targetPackage) {
    final String qualifiedName = targetPackage.getQualifiedName();
    return switch (element) {
      case PsiClass aClass -> StringUtil.getQualifiedName(qualifiedName, StringUtil.notNullize(aClass.getName()));
      case PsiPackage aPackage -> StringUtil.getQualifiedName(qualifiedName, StringUtil.notNullize(aPackage.getName()));
      case PsiClassOwner owner -> owner.getName();
      default -> throw new IllegalArgumentException("Unexpected element: " + element);
    };
  }

  private static @Nullable String getOldQName(@NotNull PsiElement element) {
    return switch (element) {
      case PsiClass aClass -> aClass.getQualifiedName();
      case PsiPackage aPackage -> aPackage.getQualifiedName();
      case PsiClassOwner owner -> owner.getName();
      default -> throw new IllegalArgumentException("Unexpected element: " + element);
    };
  }

  private static void detectMoveToDefaultPackage(UsageInfo[] infos,
                                                 MultiMap<PsiElement, @DialogMessage String> conflicts,
                                                 PackageWrapper aPackage) {
    if (!aPackage.getQualifiedName().isEmpty()) return;

    Set<PsiFile> filesWithImports = new HashSet<>();
    for (UsageInfo info : infos) {
      PsiElement element = info.getElement();
      if (element == null) continue;
      PsiReference reference = info.getReference();
      if (reference == null) continue;
      PsiElement target = reference.resolve();
      PsiFile file = element.getContainingFile();
      if (file instanceof PsiJavaFile && target != null && filesWithImports.add(file) && !((PsiJavaFile)file).getPackageName().isEmpty()) {
        conflicts.putValue(element, JavaBundle.message("move.class.import.from.default.package.conflict", RefactoringUIUtil.getDescription(target, false)));
      }
    }
  }

  private static boolean isInsideMoved(PsiElement place, PsiElement[] elementsToMove) {
    for (PsiElement element : elementsToMove) {
      if (element instanceof PsiClass) {
        if (PsiTreeUtil.isAncestor(element, place, false)) return true;
      }
    }
    return false;
  }

  static void detectPackageLocalsUsed(MultiMap<PsiElement, @DialogMessage String> conflicts,
                                      PsiElement[] elementsToMove,
                                      PackageWrapper targetPackage) {
    PackageLocalsUsageCollector visitor = new PackageLocalsUsageCollector(elementsToMove, targetPackage, conflicts);

    for (PsiElement element : elementsToMove) {
      if (element.getContainingFile() != null && !(element instanceof PsiCompiledElement)) {
        element.accept(visitor);
      }
    }
  }

  private static void detectPackageLocalsMoved(UsageInfo[] usages,
                                               MultiMap<PsiElement, @DialogMessage String> conflicts,
                                               @NotNull PackageWrapper targetPackage,
                                               PsiElement[] elementsToMove) {
    Set<PsiClass> movedClasses = new HashSet<>();
    Map<PsiClass,Set<PsiElement>> reportedClassToContainers = new HashMap<>();
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element == null) continue;
      if (usage instanceof MoveRenameUsageInfo moveRenameUsageInfo && !(usage instanceof NonCodeUsageInfo) &&
          moveRenameUsageInfo.getReferencedElement() instanceof PsiClass aClass) {
        movedClasses.add(aClass);
        if (aClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
          if (PsiTreeUtil.getParentOfType(element, PsiImportStatement.class) != null) continue;
          PsiElement container = ConflictsUtil.getContainer(element);
          Set<PsiElement> reported = reportedClassToContainers.computeIfAbsent(aClass, _ -> new HashSet<>());

          if (!reported.contains(container)) {
            reported.add(container);
            PsiFile containingFile = element.getContainingFile();
            if (containingFile != null && !isInsideMoved(element, elementsToMove)) {
              PsiDirectory directory = containingFile.getContainingDirectory();
              if (directory != null) {
                PsiPackage usagePackage = JavaDirectoryService.getInstance().getPackage(directory);
                if (usagePackage != null && !targetPackage.equalToPackage(usagePackage)) {
                  final String message = JavaRefactoringBundle.message("a.package.local.class.0.will.no.longer.be.accessible.from.1",
                                                                       CommonRefactoringUtil.htmlEmphasize(aClass.getName()),
                                                                       RefactoringUIUtil.getDescription(
                                                                   container, true));
                  conflicts.putValue(aClass, message);
                }
              }
            }
          }
        }
      }
    }

    final MyClassInstanceReferenceVisitor instanceReferenceVisitor = new MyClassInstanceReferenceVisitor(targetPackage, elementsToMove, conflicts);
    for (final PsiClass aClass : movedClasses) {
      String visibility = VisibilityUtil.getVisibilityModifier(aClass.getModifierList());
      if (PsiModifier.PACKAGE_LOCAL.equals(visibility)) {
        findInstancesOfPackageLocal(aClass, usages, instanceReferenceVisitor);
      }
      else {
        // public classes
        findPublicClassConflicts(aClass, instanceReferenceVisitor);
      }
    }
  }

  private static void findPublicClassConflicts(PsiClass aClass, final MyClassInstanceReferenceVisitor instanceReferenceVisitor) {
    NonPublicClassMemberWrappersSet members = new NonPublicClassMemberWrappersSet();

    members.addElements(aClass.getFields());
    members.addElements(aClass.getMethods());
    members.addElements(aClass.getInnerClasses());

    final RefactoringUtil.IsDescendantOf isDescendantOf = new RefactoringUtil.IsDescendantOf(aClass);
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(aClass.getContainingFile().getContainingDirectory());
    final GlobalSearchScope packageScope = aPackage == null ? aClass.getResolveScope() : PackageScope.packageScopeWithoutLibraries(aPackage, false);
    for (final ClassMemberWrapper memberWrapper : members) {
      ReferencesSearch.search(memberWrapper.getMember(), packageScope, false).forEach(reference -> {
        final PsiElement element = reference.getElement();
        if (element instanceof PsiReferenceExpression expression) {
          final PsiExpression qualifierExpression = expression.getQualifierExpression();
          if (qualifierExpression != null) {
            final PsiType type = qualifierExpression.getType();
            if (type != null) {
              final PsiClass resolvedTypeClass = PsiUtil.resolveClassInType(type);
              if (isDescendantOf.value(resolvedTypeClass)) {
                instanceReferenceVisitor.visitMemberReference(memberWrapper.getMember(), expression, isDescendantOf);
              }
            }
          }
          else {
            instanceReferenceVisitor.visitMemberReference(memberWrapper.getMember(), expression, isDescendantOf);
          }
        }
        return true;
      });
    }
  }

  private static void findInstancesOfPackageLocal(final PsiClass aClass,
                                                  final UsageInfo[] usages,
                                                  final MyClassInstanceReferenceVisitor instanceReferenceVisitor) {
    ClassReferenceScanner referenceScanner = new ClassReferenceScanner(aClass) {
      @Override
      public PsiReference[] findReferences() {
        ArrayList<PsiReference> result = new ArrayList<>();
        for (UsageInfo usage : usages) {
          if (usage instanceof MoveRenameUsageInfo && ((MoveRenameUsageInfo)usage).getReferencedElement() == aClass) {
            final PsiReference reference = usage.getReference();
            if (reference != null) {
              result.add(reference);
            }
          }
        }
        return result.toArray(PsiReference.EMPTY_ARRAY);
      }
    };
    referenceScanner.processReferences(new ClassInstanceScanner(aClass, instanceReferenceVisitor));
  }

  /**
   * Stores usages that are potentially affected during the move file(s) refactoring.
   * <p>
   * {@code affectedUsages = allUsages - usagesToSkip}.
   * </p>
   * @param allUsages usages that participate in conflict detection.
   * @param usagesToSkip usages that should be ignored during move refactoring.
   *
   * @see MoveClassesOrPackagesUtil#findUsagesInElements(PsiElement[], SearchScope, boolean, boolean, PackageWrapper)
   */
  public record UsagesContext(@NotNull List<UsageInfo> allUsages, @NotNull List<UsageInfo> usagesToSkip) {
  }

  private static class MyClassInstanceReferenceVisitor implements ClassInstanceScanner.ClassInstanceReferenceVisitor {
    private final MultiMap<PsiElement, @DialogMessage String> myConflicts;
    private final Map<PsiModifierListOwner, HashSet<PsiElement>> myReportedElementToContainer = new HashMap<>();
    private final Map<PsiClass, RefactoringUtil.IsDescendantOf> myIsDescendantOfCache = new HashMap<>();
    private final @NotNull PackageWrapper myTargetPackage;
    private final PsiElement[] myElementsToMove;

    MyClassInstanceReferenceVisitor(@NotNull PackageWrapper targetPackage,
                                    PsiElement[] elementsToMove,
                                    MultiMap<PsiElement, @DialogMessage String> conflicts) {
      myConflicts = conflicts;
      myTargetPackage = targetPackage;
      myElementsToMove = elementsToMove;
    }

    @Override
    public void visitQualifier(PsiReferenceExpression qualified,
                               PsiExpression instanceRef,
                               PsiElement referencedInstance) {
      PsiElement resolved = qualified.resolve();

      if (resolved instanceof PsiMember member) {
        final PsiClass containingClass = member.getContainingClass();
        RefactoringUtil.IsDescendantOf isDescendantOf = myIsDescendantOfCache.get(containingClass);
        if (isDescendantOf == null) {
          isDescendantOf = new RefactoringUtil.IsDescendantOf(containingClass);
          myIsDescendantOfCache.put(containingClass, isDescendantOf);
        }
        visitMemberReference(member, qualified, isDescendantOf);
      }
    }

    private synchronized void visitMemberReference(final PsiModifierListOwner member, PsiReferenceExpression qualified, final RefactoringUtil.IsDescendantOf descendantOf) {
      if (member.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        visitPackageLocalMemberReference(qualified, member, myElementsToMove);
      } else if (member.hasModifierProperty(PsiModifier.PROTECTED)) {
        final PsiExpression qualifier = qualified.getQualifierExpression();
        if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
          visitPackageLocalMemberReference(qualified, member, myElementsToMove);
        } else {
          if (!isInInheritor(qualified, descendantOf)) {
            visitPackageLocalMemberReference(qualified, member, myElementsToMove);
          }
        }
      }
    }

    private static boolean isInInheritor(PsiReferenceExpression qualified, final RefactoringUtil.IsDescendantOf descendantOf) {
      PsiClass aClass = PsiTreeUtil.getParentOfType(qualified, PsiClass.class);
      while (aClass != null) {
        if (descendantOf.value(aClass)) return true;
        aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
      }
      return false;
    }

    private void visitPackageLocalMemberReference(PsiJavaCodeReferenceElement qualified,
                                                  PsiModifierListOwner member, PsiElement[] elementsToMove) {
      PsiElement container = ConflictsUtil.getContainer(qualified);
      Set<PsiElement> reportedContainers = myReportedElementToContainer.computeIfAbsent(member, _ -> new HashSet<>());

      if (!reportedContainers.contains(container)) {
        reportedContainers.add(container);
        if (!isInsideMoved(container, elementsToMove)) {
          PsiFile containingFile = container.getContainingFile();
          if (containingFile != null) {
            PsiDirectory directory = containingFile.getContainingDirectory();
            if (directory != null) {
              PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
              if (!myTargetPackage.equalToPackage(aPackage)) {
                String message = JavaRefactoringBundle.message("0.will.be.inaccessible.from.1", RefactoringUIUtil.getDescription(member, true),
                                                               RefactoringUIUtil.getDescription(container, true));
                myConflicts.putValue(member, StringUtil.capitalize(message));
              }
            }
          }
        }
      }
    }

    @Override
    public void visitTypeCast(PsiTypeCastExpression typeCastExpression,
                              PsiExpression instanceRef,
                              PsiElement referencedInstance) {
    }

    @Override
    public void visitReadUsage(PsiExpression instanceRef, PsiType expectedType, PsiElement referencedInstance) {
    }

    @Override
    public void visitWriteUsage(PsiExpression instanceRef, PsiType assignedType, PsiElement referencedInstance) {
    }
  }

  private static class NonPublicClassMemberWrappersSet extends HashSet<ClassMemberWrapper> {
    public void addElement(PsiMember member) {
      final PsiNamedElement namedElement = (PsiNamedElement)member;
      if (member.hasModifierProperty(PsiModifier.PUBLIC)) return;
      if (member.hasModifierProperty(PsiModifier.PRIVATE)) return;
      add(new ClassMemberWrapper(namedElement));
    }

    public void addElements(PsiMember[] members) {
      for (PsiMember member : members) {
        addElement(member);
      }
    }
  }

  static class ClassMemberWrapper {
    final PsiNamedElement myElement;
    final PsiModifierListOwner myMember;

    ClassMemberWrapper(PsiNamedElement element) {
      myElement = element;
      myMember = (PsiModifierListOwner) element;
    }

    PsiModifierListOwner getMember() {
      return myMember;
    }


    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ClassMemberWrapper wrapper)) return false;

      if (myElement instanceof PsiMethod method) {
        return wrapper.myElement instanceof PsiMethod wrapperMethod &&
               MethodSignatureUtil.areSignaturesEqual(method, wrapperMethod);
      }

      return Objects.equals(myElement.getName(), wrapper.myElement.getName());
    }

    @Override
    public int hashCode() {
      final String name = myElement.getName();
      if (name != null) {
        return name.hashCode();
      }
      else {
        return 0;
      }
    }
  }
}
