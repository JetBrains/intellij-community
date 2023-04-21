// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class LombokLibraryUtil {

  private static final Logger LOG = Logger.getInstance(LombokLibraryUtil.class);

  private static final String LOMBOK_PACKAGE = "lombok.experimental";

  public static boolean hasLombokLibrary(@NotNull Project project) {
    if (project.isDefault() || !project.isInitialized()) {
      return false;
    }
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(LOMBOK_PACKAGE);
      return new CachedValueProvider.Result<>(aPackage, ProjectRootManager.getInstance(project));
    }) != null;
  }

  @NotNull
  public static String getLombokVersionCached(@NotNull Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      String lombokVersion = null;
      try {
        lombokVersion = ReadAction.nonBlocking(() -> getLombokVersionInternal(project)).executeSynchronously();
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
      return new CachedValueProvider.Result<>(StringUtil.notNullize(lombokVersion), ProjectRootManager.getInstance(project));
    });
  }

  @Nullable
  private static String getLombokVersionInternal(@NotNull Project project) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(LOMBOK_PACKAGE);
    if (aPackage != null) {
      PsiDirectory[] directories = aPackage.getDirectories();
      if (directories.length > 0) {
        List<OrderEntry> entries =
          ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(directories[0].getVirtualFile());
        if (!entries.isEmpty()) {
          return LombokVersion.parseLombokVersion(entries.get(0));
        }
      }
    }
    return null;
  }
}
