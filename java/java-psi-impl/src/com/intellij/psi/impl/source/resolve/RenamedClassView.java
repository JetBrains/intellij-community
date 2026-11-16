// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.impl.light.LightClass;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

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

  /**
   * @param toRename       the class to rename
   * @param ignoredImports the imports that the rename deletes before the write, see {@link ResolveAfterRenameUtil#resolveClass}
   * @param renamedCopy    the copy of {@code toRename} that has the new name already, in the copy of its file; null if there is none
   */
  public RenamedClassView(@NotNull PsiClass toRename,
                          @NotNull String newName,
                          @NotNull @Unmodifiable Collection<? extends PsiImportStatementBase> ignoredImports,
                          @Nullable PsiClass renamedCopy) {
    super(toRename);
    myToRename = toRename;
    myNewName = newName;
    myIgnoredImports = ignoredImports;
    myRenamedCopy = renamedCopy;
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
    return qualifiedName == null ? null : StringUtil.getQualifiedName(StringUtil.getPackageName(qualifiedName), myNewName);
  }

  @NotNull @Unmodifiable Collection<? extends PsiImportStatementBase> getIgnoredImports() {
    return myIgnoredImports;
  }

  /** @return whether {@code element} is the class to rename or its renamed copy */
  boolean isRenamed(@Nullable PsiElement element) {
    return element == myToRename || element != null && element == myRenamedCopy;
  }
}
