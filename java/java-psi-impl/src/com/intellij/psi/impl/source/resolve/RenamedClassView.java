// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.impl.light.LightClass;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * A class that is not renamed yet, under its new name. The resolve of {@link ResolveAfterRenameUtil#resolveClass} offers it instead of the
 * class. A Java reference that binds to it decides its form after the rename, see
 * {@link com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl#formAfterRename}.
 */
@ApiStatus.Internal
public final class RenamedClassView extends LightClass {
  private final @NotNull PsiClass myToRename;
  private final @NotNull String myNewName;
  private final @NotNull @Unmodifiable Collection<? extends PsiImportStatementBase> myIgnoredImports;
  private final @Nullable PsiClass myRenamedCopy;
  private final @Nullable String myTargetPackage;
  private final @NotNull @Unmodifiable Collection<? extends PsiFile> myMovedFiles;

  /**
   * @param toRename       the class to rename
   * @param ignoredImports the imports that the rename deletes before the write, see {@link ResolveAfterRenameUtil#resolveClass}
   * @param renamedCopy    the copy of {@code toRename} that has the new name already, in the copy of its file; null if there is none
   */
  public RenamedClassView(@NotNull PsiClass toRename,
                          @NotNull String newName,
                          @NotNull @Unmodifiable Collection<? extends PsiImportStatementBase> ignoredImports,
                          @Nullable PsiClass renamedCopy) {
    this(toRename, newName, ignoredImports, renamedCopy, null, Collections.emptyList());
  }

  private RenamedClassView(@NotNull PsiClass toRename,
                           @NotNull String newName,
                           @NotNull @Unmodifiable Collection<? extends PsiImportStatementBase> ignoredImports,
                           @Nullable PsiClass renamedCopy,
                           @Nullable String targetPackage,
                           @NotNull @Unmodifiable Collection<? extends PsiFile> movedFiles) {
    super(toRename);
    myToRename = toRename;
    myNewName = newName;
    myIgnoredImports = ignoredImports;
    myRenamedCopy = renamedCopy;
    myTargetPackage = targetPackage;
    myMovedFiles = movedFiles;
  }

  /**
   * The view of the top-level class {@code originalClass} under the package {@code targetPackage}, with its name kept.
   *
   * @param movedFiles the files that go to {@code targetPackage} with the class; the file of the class is among them when it moves
   *                   as a whole
   */
  public static @NotNull RenamedClassView moved(@NotNull PsiClass originalClass,
                                                @NotNull String targetPackage,
                                                @NotNull Collection<? extends PsiFile> movedFiles) {
    return new RenamedClassView(originalClass, Objects.requireNonNull(originalClass.getName()), Collections.emptyList(), null,
                                targetPackage, new ArrayList<>(movedFiles));
  }

  /** @return whether the package of the class changes */
  public boolean isMoved() {
    String qualifiedName = myToRename.getQualifiedName();
    return myTargetPackage != null && qualifiedName != null && !myTargetPackage.equals(StringUtil.getPackageName(qualifiedName));
  }

  public @NotNull PsiClass getToRename() {
    return myToRename;
  }

  @Override
  public @NotNull String getName() {
    return myNewName;
  }

  @Override
  public @Nullable String getQualifiedName() {
    String qualifiedName = myToRename.getQualifiedName();
    if (qualifiedName == null) return null;
    return StringUtil.getQualifiedName(myTargetPackage != null ? myTargetPackage : StringUtil.getPackageName(qualifiedName), myNewName);
  }

  @NotNull String getTargetPackage() {
    return Objects.requireNonNull(myTargetPackage);
  }

  /** @return whether {@code file} or its original file goes to the target package with the moved class */
  boolean isMovedFile(@NotNull PsiFile file) {
    return myMovedFiles.contains(file) || myMovedFiles.contains(file.getOriginalFile());
  }

  @NotNull @Unmodifiable Collection<? extends PsiImportStatementBase> getIgnoredImports() {
    return myIgnoredImports;
  }

  /**
   * @return whether {@code element} is the class to rename or its renamed copy; for a moved class, also the class of the same name in a
   * non-physical copy of its file
   */
  boolean isRenamed(@Nullable PsiElement element) {
    if (element == myToRename || element != null && element == myRenamedCopy) return true;
    if (!isMoved() || !(element instanceof PsiClass) || ((PsiClass)element).getContainingClass() != null) return false;
    PsiFile file = element.getContainingFile();
    return file != null && myNewName.equals(((PsiClass)element).getName()) && file.getOriginalFile() == myToRename.getContainingFile();
  }
}
