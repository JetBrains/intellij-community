/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.scratch;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author gregsh
 *
 * Created on 1/19/15
 */
public abstract class RootType {

  public static final ExtensionPointName<RootType> ROOT_EP = ExtensionPointName.create("com.intellij.scratch.rootType");

  public static RootType[] getAllRootIds() {
    return Extensions.getExtensions(ROOT_EP);
  }

  private final String myId;
  private final String myDisplayName;

  protected RootType(@NotNull String id, @Nullable String displayName) {
    myId = id;
    myDisplayName = displayName;
  }

  @NotNull
  public final String getId() {
    return myId;
  }

  @Nullable
  public final String getDisplayName() {
    return myDisplayName;
  }

  public boolean isHidden() {
    return myDisplayName == null;
  }

  public boolean canBeProject() { return true; }

  @Nullable
  public Language substituteLanguage(@NotNull Project project, @NotNull VirtualFile file) {
    return null;
  }

  @Nullable
  public Icon substituteIcon(@NotNull Project project, @NotNull VirtualFile file) {
    Language language = substituteLanguage(project, file);
    LanguageFileType fileType = language != null ? language.getAssociatedFileType() : null;
    return fileType != null ? fileType.getIcon() : null;
  }

  @Nullable
  public String substituteName(@NotNull Project project, @NotNull VirtualFile file) {
    return null;
  }
}
