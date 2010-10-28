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

package com.intellij.facet.impl.ui;

import com.intellij.facet.Facet;
import org.jetbrains.annotations.Nullable;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetInfo;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.module.ModuleType;

import java.util.Collection;

/**
 * @author nik
 */
public interface FacetEditorFacade {

  boolean nodeHasFacetOfType(final @Nullable FacetInfo facet, FacetTypeId typeId);

  @Nullable
  FacetInfo getSelectedFacetInfo();

  @Nullable
  ModuleType getSelectedModuleType();

  Facet createFacet(final FacetInfo parent, final FacetType type);

  Collection<FacetInfo> getFacetsByType(FacetType<?,?> type);

  @Nullable
  FacetInfo getParent(final FacetInfo facet);
}
