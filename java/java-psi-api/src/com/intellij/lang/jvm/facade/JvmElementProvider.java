// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.facade;

import com.intellij.lang.jvm.JvmClass;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Allows to extend the mechanism of locating classes and packages by full-qualified name.
 *
 * Similar to {@link PsiElementFinder}, but for JVM API.
 */
public interface JvmElementProvider {

  ExtensionPointName<JvmElementProvider> EP_NAME = ExtensionPointName.create("com.intellij.jvm.elementProvider");

  /**
   * Searches classes within the project for a class with the specified full-qualified name.
   * @param qualifiedName the full-qualified name of the class to find
   * @param scope - the scope to search in.
   * @return list of found classes
   */
  @NotNull
  List<? extends JvmClass> getClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope);
}
