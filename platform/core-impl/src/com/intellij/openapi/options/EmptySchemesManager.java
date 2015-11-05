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
package com.intellij.openapi.options;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class EmptySchemesManager extends SchemesManager {
  @Override
  @NotNull
  public Collection loadSchemes() {
    return Collections.emptySet();
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
  public File getRootDirectory() {
    return null;
  }

  @Override
  public void setCurrentSchemeName(@Nullable String schemeName, boolean notify) {
  }
}
