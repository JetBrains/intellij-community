/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
class FileIconKey {
  private final VirtualFile myFile;
  private final Project myProject;
  @Iconable.IconFlags private final int myFlags;
  @Nullable private final Language myInitialLanguage;

  FileIconKey(@NotNull VirtualFile file, final Project project, @Iconable.IconFlags int flags) {
    myFile = file;
    myInitialLanguage = myFile instanceof LightVirtualFile ? ((LightVirtualFile)myFile).getLanguage() : null;
    myProject = project;
    myFlags = flags;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof FileIconKey)) return false;

    final FileIconKey that = (FileIconKey)o;

    if (myFlags != that.myFlags) return false;
    if (!myFile.equals(that.myFile)) return false;
    if (!Comparing.equal(myInitialLanguage, that.myInitialLanguage)) return false;
    if (myProject != null ? !myProject.equals(that.myProject) : that.myProject != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFile.hashCode();
    result = 31 * result + (myProject != null ? myProject.hashCode() : 0);
    result = 31 * result + myFlags;
    return result;
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public Project getProject() {
    return myProject;
  }

  @Iconable.IconFlags
  public int getFlags() {
    return myFlags;
  }
}