/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import java.util.List;

/**
 * @author nik
 */
public abstract class LibraryDetector<P extends LibraryProperties> {
  public static final ExtensionPointName<LibraryDetector> EP_NAME = ExtensionPointName.create("com.intellij.library.detector");
  private final LibraryKind<P> myKind;

  protected LibraryDetector(LibraryKind<P> kind) {
    myKind = kind;
  }

  public final LibraryKind<P> getKind() {
    return myKind;
  }

  @Nullable
  public abstract P detect(@NotNull List<VirtualFile> classesRoots);
}
