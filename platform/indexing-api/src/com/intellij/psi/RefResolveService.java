// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "soon, i swear")
public abstract class RefResolveService {
  /**
   * if true then PsiElement.getUseScope() returns scope restricted to only relevant files which are stored in {@link com.intellij.psi.RefResolveService}
   */
  public static final boolean ENABLED = /*ApplicationManager.getApplication().isUnitTestMode() ||*/ Boolean.getBoolean("ref.back");

  public static RefResolveService getInstance(Project project) {
    return project.getComponent(RefResolveService.class);
  }

  @Nullable("null means the service has not resolved all files and is not ready yet")
  public abstract int[] getBackwardIds(@NotNull VirtualFileWithId file);

  /**
   * @return subset of scope containing only files which reference the virtualFile
   */
  @NotNull
  public abstract GlobalSearchScope restrictByBackwardIds(@NotNull VirtualFile virtualFile, @NotNull GlobalSearchScope scope);

  /**
   * @return add files to the resolve queue. until all files from there are resolved, the service is in incomplete state and returns null from getBackwardIds()
   */
  public abstract boolean queue(@NotNull Collection<VirtualFile> files, @NotNull Object reason);

  public abstract boolean isUpToDate();

  public abstract int getQueueSize();

  public abstract void addListener(@NotNull Disposable parent, @NotNull Listener listener);

  public abstract static class Listener {
    public void fileResolved(@NotNull VirtualFile virtualFile) {}

    public void allFilesResolved() {}
  }
}
