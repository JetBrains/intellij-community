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
package com.intellij.ide.util.importProject;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.impl.FrameworkDetectionContextBase;
import com.intellij.ide.util.projectWizard.SourcePathsBuilder;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class FrameworkDetectionInWizardContext extends FrameworkDetectionContextBase {
  private final SourcePathsBuilder myBuilder;

  public FrameworkDetectionInWizardContext(SourcePathsBuilder builder) {
    myBuilder = builder;
  }

  @NotNull
  @Override
  public <F extends Facet, C extends FacetConfiguration> List<? extends DetectedFrameworkDescription> createDetectedFacetDescriptions(@NotNull FacetType<F, C> facetType,
                                                                                                                                      @NotNull Collection<VirtualFile> files,
                                                                                                                                      @NotNull FacetConfigurationCreator<C> creator) {
    return Collections.emptyList();
  }

  public VirtualFile getBaseDir() {
    final String path = myBuilder.getContentEntryPath();
    return path != null ? LocalFileSystem.getInstance().refreshAndFindFileByPath(path) : null;
  }
}
