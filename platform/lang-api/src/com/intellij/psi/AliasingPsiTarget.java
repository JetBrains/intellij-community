/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomRenameableTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AliasingPsiTarget extends DelegatePsiTarget implements PomRenameableTarget<AliasingPsiTarget>{
  public AliasingPsiTarget(@NotNull PsiNamedElement element) {
    super(element);
  }

  public boolean isWritable() {
    return getNavigationElement().isWritable();
  }

  public AliasingPsiTarget setName(@NotNull String newName) {
    return setAliasName(newName);
  }

  @NotNull
  public String getName() {
    return StringUtil.notNullize(getNameAlias(((PsiNamedElement)getNavigationElement()).getName()));
  }

  @Nullable
  public AliasingPsiTarget setAliasName(@Nullable String newAliasName) {
    return this;
  }

  @Nullable
  public String getNameAlias(@Nullable String delegatePsiTargetName) {
    return delegatePsiTargetName;
  }

}