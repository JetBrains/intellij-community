// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.ide.util.PackageUtil;
import com.intellij.model.SymbolPointer;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiSyntheticClass;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.refactoring.modplan.ModPlanAnalysisResult;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.refactoring.modplan.move.ModPlanMovePlaces;
import com.intellij.refactoring.modplan.move.ModPlanMoveProblem;
import com.intellij.refactoring.modplan.move.ModPlanMoveProvider;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The Java move of Java files on the ModPlan engine. A subject is a Java file, as the language server gets the path of a file. The file
 * moves as a whole with its classes, and the references to the classes change as {@link MoveClassesOrPackagesProcessor} changes them.
 * The provider calls no {@link com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler}.
 * <p>
 * The provider refuses with {@link ModPlanAnalysisResult.RefusedReason#NOT_SUPPORTED} a move that changes {@code module-info.java}. A
 * class, a package or a directory has no owner here.
 */
@ApiStatus.Internal
public final class ModPlanJavaMoveProvider implements ModPlanMoveProvider {
  /**
   * Whether the provider owns {@code subject}, as {@link JavaMoveClassesOrPackagesHandler#invalid4Move} decides.
   * <p>
   * The provider owns a Java file of a Java source root that has classes, or that is {@code package-info.java}, and that is not a server
   * page.
   */
  @RequiresReadLock
  @Override
  public boolean isOwner(@NotNull SymbolPointer subject) {
    var element = subject.element();
    if (element instanceof PsiJavaFile file) {
      var classes = file.getClasses();
      return (classes.length > 0 || PackageUtil.isPackageInfoFile(file)) &&
             !ContainerUtil.exists(classes, aClass -> aClass instanceof PsiSyntheticClass) && isMovableFile(file);
    }
    return false;
  }

  /**
   * Gives each file one item move to {@code targetDirectory}, with the name of the file.
   * <p>
   * The provider refuses with {@link ModPlanMoveProblem#INVALID_SUBJECT} a subject that is gone.
   * It refuses with {@link ModPlanMoveProblem#INVALID_DESTINATION} a target with no package, such as a resource root, or a directory
   * whose name is not an identifier.
   */
  @RequiresReadLock
  @Override
  public @NotNull Placement place(@NotNull List<SymbolPointer> subjects,
                                  @NotNull VirtualFile targetDirectory,
                                  @NotNull ModPlanRequest request) {
    var first = subjects.getFirst().element();
    var move = ModPlanJavaClassMove.of(subjects);
    if (move == null) return new Placement.Refused(ModPlanMoveProblem.INVALID_SUBJECT, first);
    if (ModPlanJavaMoveUtil.futurePackageOf(request.project(), targetDirectory) == null) {
      return new Placement.Refused(ModPlanMoveProblem.INVALID_DESTINATION, first);
    }
    var itemMoves = new ArrayList<ItemMove>();
    for (var file : move.wholeFiles()) {
      itemMoves.add(new ItemMove(file.getVirtualFile(), targetDirectory, file.getName()));
    }
    return new Placement.Accepted(itemMoves, List.of());
  }

  /**
   * Gives the edits and the conflicts of the move of the subjects to the package of {@code targetDirectory}.
   * <p>
   * The provider refuses with {@link ModPlanAnalysisResult.RefusedReason#NOT_APPLICABLE} a subject that is gone, or a target with no
   * package. For the other refusals, see {@link ModPlanJavaClassMove#analyze}.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @Override
  public @NotNull Analysis analyze(@NotNull List<SymbolPointer> subjects,
                                   @NotNull VirtualFile targetDirectory,
                                   @NotNull ModPlanMovePlaces places,
                                   @NotNull ModPlanRequest request) {
    var move = ModPlanJavaClassMove.of(subjects);
    var targetPackage = ModPlanJavaMoveUtil.futurePackageOf(request.project(), targetDirectory);
    if (move == null || targetPackage == null) {
      return new Analysis(new ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE), List.of(), null);
    }
    return move.analyze(targetPackage, targetDirectory, request);
  }

  private static boolean isMovableFile(@NotNull PsiJavaFile file) {
    var virtualFile = file.getVirtualFile();
    return virtualFile != null && !(file instanceof PsiCompiledElement) && !FileTypeUtils.isInServerPageFile(file) &&
           !JavaProjectRootsUtil.isOutsideJavaSourceRoot(file) && !ProjectFileIndex.getInstance(file.getProject()).isInLibrary(virtualFile);
  }
}