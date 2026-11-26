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
 * A class that is not changed yet, under its new name or package. The resolve of {@link ResolveAfterChangeUtil#resolveClass} offers it
 * instead of the class. A Java reference that binds to it decides its form after the change, see
 * {@link com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl#formAfterChange}.
 */
@ApiStatus.Internal
public final class ChangedClassView extends LightClass {
  private final @NotNull PsiClass myOriginalClass;
  private final @NotNull String myNewName;
  private final @NotNull @Unmodifiable Collection<? extends PsiImportStatementBase> myIgnoredImports;
  private final @Nullable PsiClass myChangedCopy;
  private final @Nullable String myTargetPackage;
  private final @NotNull @Unmodifiable Collection<? extends PsiFile> myMovedFiles;

  /**
   * @param originalClass  the class to change
   * @param ignoredImports the imports that the rename deletes before the write, see {@link ResolveAfterChangeUtil#resolveClass}
   * @param changedCopy    the copy of {@code originalClass} that has the new name already, in the copy of its file; null if there is none
   */
  public ChangedClassView(@NotNull PsiClass originalClass,
                          @NotNull String newName,
                          @NotNull @Unmodifiable Collection<? extends PsiImportStatementBase> ignoredImports,
                          @Nullable PsiClass changedCopy) {
    this(originalClass, newName, ignoredImports, changedCopy, null, Collections.emptyList());
  }

  private ChangedClassView(@NotNull PsiClass originalClass,
                           @NotNull String newName,
                           @NotNull @Unmodifiable Collection<? extends PsiImportStatementBase> ignoredImports,
                           @Nullable PsiClass changedCopy,
                           @Nullable String targetPackage,
                           @NotNull @Unmodifiable Collection<? extends PsiFile> movedFiles) {
    super(originalClass);
    myOriginalClass = originalClass;
    myNewName = newName;
    myIgnoredImports = ignoredImports;
    myChangedCopy = changedCopy;
    myTargetPackage = targetPackage;
    myMovedFiles = movedFiles;
  }

  /**
   * The view of the top-level class {@code originalClass} under the package {@code targetPackage}. The class keeps its name.
   *
   * @param movedFiles the files that go to {@code targetPackage} with the class; the file of the class is among them when it moves
   *                   as a whole
   */
  public static @NotNull ChangedClassView moved(@NotNull PsiClass originalClass,
                                                @NotNull String targetPackage,
                                                @NotNull Collection<? extends PsiFile> movedFiles) {
    return new ChangedClassView(originalClass, Objects.requireNonNull(originalClass.getName()), Collections.emptyList(), null,
                                targetPackage, new ArrayList<>(movedFiles));
  }

  /** @return whether the package of the class changes */
  public boolean isMoved() {
    if (myTargetPackage == null) return false;
    String qualifiedName = myOriginalClass.getQualifiedName();
    return qualifiedName != null && !myTargetPackage.equals(StringUtil.getPackageName(qualifiedName));
  }

  public @NotNull PsiClass getOriginalClass() {
    return myOriginalClass;
  }

  @Override
  public @NotNull String getName() {
    return myNewName;
  }

  @Override
  public @Nullable String getQualifiedName() {
    String qualifiedName = myOriginalClass.getQualifiedName();
    if (qualifiedName == null) return null;
    return StringUtil.getQualifiedName(myTargetPackage != null ? myTargetPackage : StringUtil.getPackageName(qualifiedName), myNewName);
  }

  /** A moved view is equivalent to a view of the same class only, not to the class itself. */
  @Override
  public boolean isEquivalentTo(PsiElement another) {
    if (!isMoved()) return super.isEquivalentTo(another);
    return this == another || another instanceof ChangedClassView && myOriginalClass == ((ChangedClassView)another).myOriginalClass;
  }

  /** @return the target package of a moved view; fails with {@link NullPointerException} for a rename view */
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
   * @return whether {@code element} is the class to change or its changed copy. A moved class also matches the class of the same name
   * in a non-physical copy of its file
   */
  boolean isOriginalClass(@Nullable PsiElement element) {
    if (element == myOriginalClass || element != null && element == myChangedCopy) return true;
    if (!isMoved() || !(element instanceof PsiClass) || ((PsiClass)element).getContainingClass() != null) return false;
    PsiFile file = element.getContainingFile();
    return file != null && myNewName.equals(((PsiClass)element).getName()) && file.getOriginalFile() == myOriginalClass.getContainingFile();
  }
}
