/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.options;

import com.intellij.openapi.util.WriteExternalException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public interface SchemesManager <T extends Scheme, E extends ExternalizableScheme>{
  SchemesManager EMPTY = new SchemesManager(){
    @Override
    @NotNull
    public Collection loadSchemes() {
      return Collections.emptySet();
    }

    @Override
    @NotNull
    public Collection loadSharedSchemes() {
      return Collections.emptySet();
    }

    @Override
    public void exportScheme(@NotNull ExternalizableScheme scheme, String name, String description) {
    }

    @Override
    public boolean isImportAvailable() {
      return false;
    }

    @Override
    public boolean isExportAvailable() {
      return false;
    }

    @Override
    public boolean isShared(@NotNull Scheme scheme) {
      return false;
    }

    @Override
    public void addNewScheme(@NotNull final Scheme scheme, final boolean replaceExisting) {
    }

    @Override
    public void clearAllSchemes() {
    }

    @Override
    @NotNull
    public List getAllSchemes() {
      return Collections.emptyList();
    }

    @Override
    public Scheme findSchemeByName(@NotNull String schemeName) {
      return null;
    }

    @Override
    public void save() {
    }

    @Override
    public void setCurrentSchemeName(String schemeName) {
    }

    @Override
    public Scheme getCurrentScheme() {
      return null;
    }

    @Override
    public void removeScheme(@NotNull Scheme scheme) {
    }

    @Override
    @NotNull
    public Collection getAllSchemeNames() {
      return Collections.emptySet();
    }

    @Override
    @NotNull
    public Collection loadSharedSchemes(Collection currentSchemeList) {
      return loadSharedSchemes();
    }

    @Override
    public File getRootDirectory() {
      return null;
    }
  };

  @NotNull
  Collection<E> loadSchemes();

  @Deprecated
  @SuppressWarnings({"unused", "deprecation"})
  @NotNull
  Collection<SharedScheme<E>> loadSharedSchemes();

  @SuppressWarnings({"unused", "deprecation"})
  @NotNull
  @Deprecated
  Collection<SharedScheme<E>> loadSharedSchemes(Collection<T> currentSchemeList);

  @SuppressWarnings("unused")
  @Deprecated
  void exportScheme(@NotNull E scheme, final String name, final String description) throws WriteExternalException, IOException;

  @SuppressWarnings("unused")
  @Deprecated
  boolean isImportAvailable();

  @SuppressWarnings("unused")
  @Deprecated
  boolean isExportAvailable();

  @Deprecated
  boolean isShared(@NotNull Scheme scheme);

  void addNewScheme(@NotNull T scheme, final boolean replaceExisting);

  void clearAllSchemes();

  @NotNull
  List<T> getAllSchemes();

  @Nullable
  T findSchemeByName(@NotNull String schemeName);

  void save();

  void setCurrentSchemeName(final String schemeName);

  @Nullable
  T getCurrentScheme();

  void removeScheme(@NotNull T scheme);

  @NotNull
  Collection<String> getAllSchemeNames();

  File getRootDirectory();
}
