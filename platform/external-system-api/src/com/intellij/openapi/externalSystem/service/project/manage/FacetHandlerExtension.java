/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Created by Nikita.Skvortsov
 * date: 12.09.2017.
 */
public interface FacetHandlerExtension<F extends Facet> {
  ExtensionPointName<FacetHandlerExtension> EP_NAME = ExtensionPointName.create("com.intellij.facetHandlerExtension");

  @NotNull
  default Collection<F> process(@NotNull Module module,
                                @NotNull String name,
                                @NotNull Map<String, Object> cfg,
                                @NotNull FacetManager facetManager) {
    return Collections.emptySet();
  }
  default boolean canHandle(@NotNull String typeName) {
    return false;
  }
}
