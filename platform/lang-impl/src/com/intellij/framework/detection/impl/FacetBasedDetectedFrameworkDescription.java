/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.framework.detection.impl;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetType;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.framework.FrameworkType;
import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Set;

/**
 * @author nik
 */
public class FacetBasedDetectedFrameworkDescription<C extends FacetConfiguration> extends DetectedFrameworkDescription {
  private final Module myModule;
  private final C myConfiguration;
  private final Set<VirtualFile> myRelatedFiles;
  private final FacetType<?,C> myFacetType;
  private FrameworkType myFrameworkType;

  public FacetBasedDetectedFrameworkDescription(@NotNull Module module, @NotNull C configuration, Set<VirtualFile> files, FacetType<?, C> type) {
    myModule = module;
    myConfiguration = configuration;
    myRelatedFiles = files;
    myFacetType = type;
    final Icon icon = myFacetType.getIcon();
    myFrameworkType = new FrameworkType(myFacetType.getPresentableName(), icon != null ? icon : EmptyIcon.ICON_16);
  }

  @NotNull
  @Override
  public FrameworkType getFrameworkType() {
    return myFrameworkType;
  }

  @NotNull
  @Override
  public Collection<? extends VirtualFile> getRelatedFiles() {
    return myRelatedFiles;
  }

  @NotNull
  @Override
  public String getDescription() {
    return myFacetType.getPresentableName() + " framework detected in module '" + myModule.getName() + "'";
  }

  @Override
  public void configureFramework(ModifiableModelsProvider modifiableModelsProvider) {
    final ModifiableFacetModel model = modifiableModelsProvider.getFacetModifiableModel(myModule);
    model.addFacet(FacetManager.getInstance(myModule).createFacet(myFacetType, myFacetType.getDefaultFacetName(), myConfiguration, null));
    modifiableModelsProvider.commitFacetModifiableModel(myModule, model);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof FacetBasedDetectedFrameworkDescription)) {
      return false;
    }
    final FacetBasedDetectedFrameworkDescription other = (FacetBasedDetectedFrameworkDescription)obj;
    return myModule.equals(other.myModule) && myFacetType.equals(other.myFacetType) && myRelatedFiles.equals(other.myRelatedFiles);
  }

  @Override
  public int hashCode() {
    return myModule.hashCode() + 31*myFacetType.hashCode() + 239*myRelatedFiles.hashCode();
  }
}
