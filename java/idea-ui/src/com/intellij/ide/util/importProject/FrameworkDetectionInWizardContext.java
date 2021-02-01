// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public abstract class FrameworkDetectionInWizardContext extends FrameworkDetectionContextBase {
  protected FrameworkDetectionInWizardContext() {
  }

  @NotNull
  @Override
  public <F extends Facet, C extends FacetConfiguration> List<? extends DetectedFrameworkDescription> createDetectedFacetDescriptions(@NotNull FacetBasedFrameworkDetector<F, C> detector,
                                                                                                                                      @NotNull Collection<? extends VirtualFile> files) {
    final List<ModuleDescriptor> descriptors = getModuleDescriptors();
    MultiMap<ModuleDescriptor, VirtualFile> filesByModule = new MultiMap<>();
    for (VirtualFile file : files) {
      final File ioFile = VfsUtilCore.virtualToIoFile(file);
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
        detector.createConfigurations(filesByModule.get(module), Collections.emptyList());
      for (Pair<C, Collection<VirtualFile>> pair : pairs) {
        result.add(new FacetBasedDetectedFrameworkDescriptionInWizard<>(module, detector, pair.getFirst(),
                                                                        new HashSet<>(pair.getSecond())));
      }
    }
    return result;
  }

  protected abstract List<ModuleDescriptor> getModuleDescriptors();

  @Nullable
  private static ModuleDescriptor findDescriptorByFile(List<? extends ModuleDescriptor> descriptors, File file) {
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

  @Override
  public VirtualFile getBaseDir() {
    final String path = getContentPath();
    return path != null ? LocalFileSystem.getInstance().refreshAndFindFileByPath(path) : null;
  }

  @Nullable
  protected abstract String getContentPath();
}
