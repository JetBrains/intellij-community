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
package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.*;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;

/**
 * Represents the application-level settings (JDKs and global libraries) required for an external build.
 *
 * @author nik
 * @see org.jetbrains.jps.model.JpsModel#getGlobal()
 */
public interface JpsGlobal extends JpsCompositeElement, JpsReferenceableElement<JpsGlobal> {
  @NotNull
  <P extends JpsElement, LibraryType extends JpsLibraryType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsLibrary addLibrary(@NotNull LibraryType libraryType, final @NotNull String name);

  <P extends JpsElement, SdkType extends JpsSdkType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsTypedLibrary<JpsSdk<P>> addSdk(@NotNull String name, @Nullable String homePath, @Nullable String versionString, @NotNull SdkType type);

  <P extends JpsElement>
  JpsTypedLibrary<JpsSdk<P>> addSdk(@NotNull String name, @Nullable String homePath, @Nullable String versionString,
                                    @NotNull JpsSdkType<P> type, @NotNull P properties);

  @NotNull
  JpsLibraryCollection getLibraryCollection();

  @NotNull
  JpsFileTypesConfiguration getFileTypesConfiguration();
}
