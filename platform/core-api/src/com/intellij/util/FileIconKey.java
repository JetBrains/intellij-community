// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Konstantin Bulenkov
 */
final class FileIconKey {
  private final VirtualFile myFile;
  private final Project myProject;
  private final @Iconable.IconFlags int myFlags;
  private final @Nullable Language myInitialLanguage;
  private final long myStamp;

  FileIconKey(@NotNull VirtualFile file, @Nullable Project project, @Iconable.IconFlags int flags) {
    myFile = file;
    myProject = project;
    myFlags = flags;
    myInitialLanguage = myFile instanceof LightVirtualFile ? ((LightVirtualFile)myFile).getLanguage() : null;
    myStamp = project == null ? 0 : PsiManager.getInstance(project).getModificationTracker().getModificationCount();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FileIconKey)) return false;

    FileIconKey that = (FileIconKey)o;
    if (myFlags != that.myFlags) return false;
    if (myStamp != that.myStamp) return false;
    if (!myFile.equals(that.myFile)) return false;
    if (!Objects.equals(myProject, that.myProject)) return false;

    if (!Objects.equals(myInitialLanguage, that.myInitialLanguage)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myFile, myProject, myFlags, myStamp);
  }

  VirtualFile getFile() {
    return myFile;
  }

  Project getProject() {
    return myProject;
  }

  @Iconable.IconFlags int getFlags() {
    return myFlags;
  }
}