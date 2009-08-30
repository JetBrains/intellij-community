/*
 * @author max
 */
package com.intellij.psi.impl.file.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface JavaFileManager {
  @Nullable
  PsiPackage findPackage(@NotNull String packageName);

  @Nullable
  PsiClass findClass(@NotNull String qName, @NotNull GlobalSearchScope scope);

  PsiClass[] findClasses(@NotNull String qName, @NotNull GlobalSearchScope scope);

  Collection<String> getNonTrivialPackagePrefixes();

  void initialize();

  void dispose();
}