/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.pom;

import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class PomDeclarationSearcher {
  public static final ExtensionPointName<PomDeclarationSearcher> EP_NAME = ExtensionPointName.create("com.intellij.pom.declarationSearcher");

  public abstract void findDeclarationsAt(@NotNull PsiElement element, int offsetInElement, Consumer<PomTarget> consumer);

}
