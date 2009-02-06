/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.pom.references;

import com.intellij.openapi.editor.Editor;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public abstract class PomReferenceService {

  @NotNull
  public abstract List<PomReference> findReferencesAt(@NotNull PsiElement element, int offset);

  @NotNull
  public abstract List<PomReference> getReferences(@NotNull PsiElement element);

  public abstract List<PomTarget> getReferencedTargets(@NotNull Editor editor, int offset);

  public abstract List<PomReference> findReferencesAt(@NotNull Editor editor, int offset);
}
