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
import com.intellij.framework.detection.FacetBasedFrameworkDetector;
import com.intellij.framework.detection.impl.FrameworkDetectionContextBase;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public abstract class FrameworkDetectionInWizardContext extends FrameworkDetectionContextBase {
  protected FrameworkDetectionInWizardContext() {
  }

  @NotNull
  @Override
  public <F extends Facet, C extends FacetConfiguration> List<? extends DetectedFrameworkDescription> createDetectedFacetDescriptions(@NotNull FacetBasedFrameworkDetector<F, C> detector,
                                                                                                                                      @NotNull Collection<VirtualFile> files) {
    final List<ModuleDescriptor> descriptors = getModuleDescriptors();
    MultiMap<ModuleDescriptor, VirtualFile> filesByModule = new MultiMap<>();
    for (VirtualFile file : files) {
      final File ioFile = VfsUtil.virtualToIoFile(file);
      ModuleDescriptor descriptor = findDescriptorByFile(descriptors, ioFile);
      if (descriptor != null) {
        filesByModule.putValue(descriptor, file);
      }
    }

    final List<DetectedFrameworkDescription> result = new ArrayList<>();
    final FacetType<F,C> facetType = detector.getFacetType();
    for (ModuleDescriptor module : filesByModule.keySet()) {
      if (!facetType.isSuitableModuleType(module.getModuleType())) {
        continue;
      }

      final List<Pair<C, Collection<VirtualFile>>> pairs =
        detector.createConfigurations(filesByModule.get(module), Collections.<C>emptyList());
      for (Pair<C, Collection<VirtualFile>> pair : pairs) {
        result.add(new FacetBasedDetectedFrameworkDescriptionInWizard<>(module, detector, pair.getFirst(),
                                                                        new HashSet<>(pair.getSecond())));
      }
    }
    return result;
  }

  protected abstract List<ModuleDescriptor> getModuleDescriptors();

  @Nullable
  private static ModuleDescriptor findDescriptorByFile(List<ModuleDescriptor> descriptors, File file) {
    ModuleDescriptor result = null;
    File nearestRoot = null;
    for (ModuleDescriptor descriptor : descriptors) {
      for (File root : descriptor.getContentRoots()) {
        if (FileUtil.isAncestor(root, file, false) && (nearestRoot == null || FileUtil.isAncestor(nearestRoot, root, true))) {
          result = descriptor;
          nearestRoot = root;
        }
      }
    }
    return result;
  }

  public VirtualFile getBaseDir() {
    final String path = getContentPath();
    return path != null ? LocalFileSystem.getInstance().refreshAndFindFileByPath(path) : null;
  }

  @Nullable
  protected abstract String getContentPath();
}
