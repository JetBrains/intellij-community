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
package com.intellij.ide.util.frameworkSupport;

import com.intellij.framework.library.DownloadableLibraryType;
import com.intellij.openapi.roots.libraries.LibraryKind;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public class CustomLibraryDescriptionImpl extends CustomLibraryDescriptionBase {
  private final DownloadableLibraryType myLibraryType;

  public CustomLibraryDescriptionImpl(@NotNull DownloadableLibraryType downloadableLibraryType) {
    super(downloadableLibraryType.getLibraryCategoryName());
    myLibraryType = downloadableLibraryType;
  }

  @NotNull
  @Override
  public Set<? extends LibraryKind> getSuitableLibraryKinds() {
    return Collections.singleton(myLibraryType.getKind());
  }

  @Override
  public DownloadableLibraryType getDownloadableLibraryType() {
    return myLibraryType;
  }

  @Override
  public String toString() {
    return "CustomLibraryDescriptionImpl(" + myLibraryType.getKind().getKindId() + ")";
  }
}
