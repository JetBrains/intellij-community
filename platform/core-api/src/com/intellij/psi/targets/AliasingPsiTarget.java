// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.targets;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomRenameableTarget;
import com.intellij.psi.DelegatePsiTarget;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AliasingPsiTarget extends DelegatePsiTarget implements PomRenameableTarget<AliasingPsiTarget>{
  public AliasingPsiTarget(@NotNull PsiNamedElement element) {
    super(element);
  }

  @Override
  public boolean isWritable() {
    return getNavigationElement().isWritable();
  }

  @Override
  public AliasingPsiTarget setName(@NotNull String newName) {
    return setAliasName(newName);
  }

   @Override
   @NotNull
  public String getName() {
    return StringUtil.notNullize(getNameAlias(StringUtil.notNullize(((PsiNamedElement)getNavigationElement()).getName())));
  }

  @NotNull
  public AliasingPsiTarget setAliasName(@NotNull String newAliasName) {
    return this;
  }

  @Nullable
  public String getNameAlias(@NotNull String delegatePsiTargetName) {
    return delegatePsiTargetName;
  }

  @NotNull
  public String getTargetName(@NotNull String aliasName) {
    return aliasName;
  }
}
