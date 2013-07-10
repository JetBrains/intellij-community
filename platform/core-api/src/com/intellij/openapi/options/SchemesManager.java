/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
    @NotNull
    public Collection loadSchemes() {
      return Collections.emptySet();
    }

    @NotNull
    public Collection loadSharedSchemes() {
      return Collections.emptySet();
    }

    public void exportScheme(final ExternalizableScheme scheme, final String name, final String description) {
    }

    public boolean isImportAvailable() {
      return false;
    }

    public boolean isExportAvailable() {
      return false;
    }

    public boolean isShared(final Scheme scheme) {
      return false;
    }

    public void addNewScheme(@NotNull final Scheme scheme, final boolean replaceExisting) {

    }

    public void clearAllSchemes() {
    }

    @NotNull
    public List getAllSchemes() {
      return Collections.emptyList();
    }

    public Scheme findSchemeByName(final String schemeName) {
      return null;
    }

    public void save() {
    }

    public void setCurrentSchemeName(final String schemeName) {

    }

    public Scheme getCurrentScheme() {
      return null;
    }

    public void removeScheme(final Scheme scheme) {

    }

    @NotNull
    public Collection getAllSchemeNames() {
      return Collections.emptySet();
    }

    @NotNull
    public Collection loadSharedSchemes(final Collection currentSchemeList) {
      return loadSharedSchemes();
    }

    public File getRootDirectory() {
      return null;
    }
  };

  @NotNull Collection<E> loadSchemes();

  @NotNull Collection<SharedScheme<E>> loadSharedSchemes();
  @NotNull Collection<SharedScheme<E>> loadSharedSchemes(Collection<T> currentSchemeList);

  void exportScheme(final E scheme, final String name, final String description) throws WriteExternalException, IOException;

  boolean isImportAvailable();

  boolean isExportAvailable();

  boolean isShared(final Scheme scheme);

  void addNewScheme(@NotNull T scheme, final boolean replaceExisting);

  void clearAllSchemes();

  @NotNull
  List<T> getAllSchemes();

  @Nullable
  T findSchemeByName(final String schemeName);

  void save() throws WriteExternalException;

  void setCurrentSchemeName(final String schemeName);

  @Nullable
  T getCurrentScheme();

  void removeScheme(final T scheme);

  @NotNull Collection<String> getAllSchemeNames();

  File getRootDirectory();
}
