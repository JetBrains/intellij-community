// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.modcommand.FutureVirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.refactoring.modplan.ModPlanConflicts;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Map;

/** The checks and the conversions that the Java move on the ModPlan engine shares. */
final class ModPlanJavaMoveUtil {
  private ModPlanJavaMoveUtil() {
  }

  /**
   * Computes the package of {@code directory} after the move creates its missing directories.
   * The package is the one of the nearest existing directory in the Java sources, plus the names of the missing directories.
   * The default package gives an empty string.
   *
   * <pre>{@code
   * // the package a.b has the directory src/a/b
   * futurePackageOf(src/a/b)      // "a.b"
   * futurePackageOf(src/a/b/c/d)  // "a.b.c.d": the directories c and d do not exist
   * futurePackageOf(src)          // ""
   * futurePackageOf(src/a/my-dir) // null
   * }</pre>
   *
   * @return the package; null if the directory is outside the Java sources or a name is not an identifier
   */
  static @Nullable String futurePackageOf(@NotNull Project project, @NotNull VirtualFile directory) {
    var missingNames = new ArrayDeque<String>();
    var existing = directory;
    while (existing instanceof FutureVirtualFile) {
      missingNames.addFirst(existing.getName());
      existing = existing.getParent();
    }
    var psiDirectory = existing == null ? null : PsiManager.getInstance(project).findDirectory(existing);
    var psiPackage = psiDirectory == null ? null : JavaDirectoryService.getInstance().getPackageInSources(psiDirectory);
    if (psiPackage == null) return null;
    var qualifiedName = psiPackage.getQualifiedName();
    for (var name : missingNames) {
      qualifiedName = StringUtil.getQualifiedName(qualifiedName, name);
    }
    return qualifiedName.isEmpty() || PsiNameHelper.getInstance(project).isQualifiedName(qualifiedName) ? qualifiedName : null;
  }

  /** @return whether {@code usage} is foreign: it is in a {@link PsiClassOwner} that is not a {@link PsiJavaFile}, or in a server page */
  static boolean isForeignUsage(@NotNull UsageInfo usage) {
    var file = usage.getFile();
    return file instanceof PsiClassOwner && !(file instanceof PsiJavaFile) || FileTypeUtils.isInServerPageFile(usage.getElement());
  }

  /** @return one broken conflict for each message of {@code conflicts} */
  static @NotNull ModPlanConflicts conflictsOf(@NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    var builder = ModPlanConflicts.builder();
    for (Map.Entry<PsiElement, Collection<@DialogMessage String>> entry : conflicts.entrySet()) {
      for (@DialogMessage String message : entry.getValue()) {
        builder.broken(entry.getKey(), message);
      }
    }
    return builder.build();
  }
}