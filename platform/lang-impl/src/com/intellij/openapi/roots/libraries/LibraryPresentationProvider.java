/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
 */
public abstract class LibraryPresentationProvider<P extends LibraryProperties> {
  public static final ExtensionPointName<LibraryPresentationProvider> EP_NAME = ExtensionPointName.create("com.intellij.library.presentationProvider");
  private final LibraryKind myKind;

  protected LibraryPresentationProvider(@NotNull LibraryKind kind) {
    myKind = kind;
  }

  @NotNull
  public LibraryKind getKind() {
    return myKind;
  }

  /**
   * @deprecated override {@link #getIcon(LibraryProperties)}.
   */

  @Deprecated
  @Nullable
  public Icon getIcon() {
    throw new AbstractMethodError();
  }

  @Nullable
  public Icon getIcon(@Nullable LibraryProperties properties) {
    //noinspection deprecation
    return getIcon();
  }

  @Nullable
  public String getDescription(@NotNull P properties) {
    return null;
  }

  @Nullable
  public abstract P detect(@NotNull List<VirtualFile> classesRoots);
}
