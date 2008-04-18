/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.facet.autodetecting.FacetDetectorRegistry;
import com.intellij.facet.ui.CommonFacetSettingsEditor;

import javax.swing.*;

/**
 * @author nik
 *
 * @see com.intellij.facet.FacetTypeRegistry#registerFacetType(FacetType)
 */
public abstract class FacetType<F extends Facet, C extends FacetConfiguration> {
  private final @NotNull FacetTypeId<F> myId;
  private final @NotNull String myStringId;
  private final @NotNull String myPresentableName;
  private final @Nullable FacetTypeId myUnderlyingFacetType;

  public FacetType(final @NotNull FacetTypeId<F> id, final @NotNull @NonNls String stringId, final @NotNull String presentableName, final @Nullable FacetTypeId underlyingFacetType) {
    myId = id;
    myStringId = stringId;
    myPresentableName = presentableName;
    myUnderlyingFacetType = underlyingFacetType;
  }


  public FacetType(final @NotNull FacetTypeId<F> id, final @NotNull @NonNls String stringId, final @NotNull String presentableName) {
    this(id, stringId, presentableName, null);
  }

  @NotNull
  public final FacetTypeId<F> getId() {
    return myId;
  }

  @NotNull
  public final String getStringId() {
    return myStringId;
  }

  @NotNull
  public final String getPresentableName() {
    return myPresentableName;
  }

  @NotNull @NonNls
  public String getDefaultFacetName() {
    return myPresentableName;
  }

  @Nullable
  public final FacetTypeId<?> getUnderlyingFacetType() {
    return myUnderlyingFacetType;
  }

  public void registerDetectors(FacetDetectorRegistry<C> registry) {
  }

  public abstract C createDefaultConfiguration();

  /**
   * Create a facet instance
   * @param module parent module for facet. Must be passed to {@link Facet} constructor
   * @param name name of facet. Must be passed to {@link Facet} constructor
   * @param configuration facet configuration. Must be passed to {@link Facet} constructor
   * @param underlyingFacet underlying facet. Must be passed to {@link Facet} constructor 
   * @return a created facet
   */
  public abstract F createFacet(@NotNull Module module, final String name, @NotNull C configuration, @Nullable Facet underlyingFacet);

  public boolean isOnlyOneFacetAllowed() {
    return true;
  }

  public abstract boolean isSuitableModuleType(ModuleType moduleType);

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Nullable
  public CommonFacetSettingsEditor createDefaultConfigurationEditor(@NotNull Project project, @NotNull C configuration) {
    return null;
  }
}
