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

package com.intellij.facet;

import com.intellij.openapi.roots.ProjectModelExternalSource;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.Disposable;

/**
 * @author nik
 */
public interface ModifiableFacetModel extends FacetModel {

  void addFacet(Facet facet);
  void addFacet(Facet facet, @Nullable ProjectModelExternalSource externalSource);
  void removeFacet(Facet facet);

  void rename(Facet facet, String newName);

  @Nullable
  String getNewName(Facet facet);

  void commit();

  boolean isModified();

  boolean isNewFacet(Facet facet);

  void addListener(@NotNull Listener listener, @NotNull Disposable parentDisposable);

  @FunctionalInterface
  interface Listener {
    void onChanged();
  }
}
