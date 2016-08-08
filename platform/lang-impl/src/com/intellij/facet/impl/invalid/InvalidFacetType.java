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
package com.intellij.facet.impl.invalid;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.ui.FacetEditor;
import com.intellij.facet.ui.MultipleFacetSettingsEditor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class InvalidFacetType extends FacetType<InvalidFacet, InvalidFacetConfiguration> {
  public static final FacetTypeId<InvalidFacet> TYPE_ID = new FacetTypeId<>("invalid");

  public static InvalidFacetType getInstance() {
    return ServiceManager.getService(InvalidFacetType.class);
  }

  public InvalidFacetType() {
    super(TYPE_ID, "invalid", "Invalid", null);
  }

  @Override
  public InvalidFacetConfiguration createDefaultConfiguration() {
    throw new UnsupportedOperationException();
  }

  @Override
  public InvalidFacet createFacet(@NotNull Module module,
                                  String name,
                                  @NotNull InvalidFacetConfiguration configuration,
                                  @Nullable Facet underlyingFacet) {
    return new InvalidFacet(this, module, name, configuration, underlyingFacet);
  }

  @Override
  public boolean isSuitableModuleType(ModuleType moduleType) {
    return true;
  }

  @Override
  public boolean isOnlyOneFacetAllowed() {
    return false;
  }

  @Override
  public MultipleFacetSettingsEditor createMultipleConfigurationsEditor(@NotNull Project project, @NotNull FacetEditor[] editors) {
    return new MultipleInvalidFacetEditor(editors);
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Unknown;
  }
}
