/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomRenameableTarget;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class RenameableDelegatePsiTarget extends DelegatePsiTarget implements PomRenameableTarget<RenameableDelegatePsiTarget>{
  public RenameableDelegatePsiTarget(@NotNull PsiNamedElement element) {
    super(element);
  }

  public boolean isWritable() {
    return getNavigationElement().isWritable();
  }

  public RenameableDelegatePsiTarget setName(@NotNull String newName) {
    ((PsiNamedElement)getNavigationElement()).setName(newName);
    return this;
  }

  @NotNull
  public String getName() {
    return StringUtil.notNullize(((PsiNamedElement)getNavigationElement()).getName());
  }
}
