// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.mock;

import com.intellij.framework.detection.FacetBasedFrameworkDetector;
import com.intellij.framework.detection.FileContentPattern;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.ElementPattern;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.patterns.StandardPatterns.string;

public final class MockFacetDetector extends FacetBasedFrameworkDetector<MockFacet, MockFacetConfiguration> {
  public static final String ROOT_TAG_NAME = "root";
  public static final String ROOT_TAG = "<" + ROOT_TAG_NAME + "/>";

  public MockFacetDetector() {
    super("mock-facet-detector");
  }

  @NotNull
  @Override
  public MockFacetType getFacetType() {
    return MockFacetType.getInstance();
  }

  @Override
  public void setupFacet(@NotNull MockFacet facet, ModifiableRootModel model) {
    facet.configure();
  }

  @NotNull
  @Override
  public List<Pair<MockFacetConfiguration, Collection<VirtualFile>>> createConfigurations(@NotNull Collection<VirtualFile> files,
                                                                     @NotNull Collection<MockFacetConfiguration> existentFacetConfigurations) {

    return doDetect(files, existentFacetConfigurations);
  }

  public static List<Pair<MockFacetConfiguration, Collection<VirtualFile>>> doDetect(Collection<VirtualFile> files,
                                                                                     Collection<MockFacetConfiguration> existentFacetConfigurations) {
    final List<Pair<MockFacetConfiguration, Collection<VirtualFile>>> result = new ArrayList<>();
    MultiMap<String, VirtualFile> filesByName = new MultiMap<>();
    for (VirtualFile file : files) {
      filesByName.putValue(file.getName(), file);
    }
    for (String name : filesByName.keySet()) {
      final MockFacetConfiguration configuration = detectConfiguration(name, existentFacetConfigurations);
      if (configuration != null) {
        result.add(Pair.create(configuration, filesByName.get(name)));
      }
    }
    return result;
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return StdFileTypes.XML;
  }

  @NotNull
  @Override
  public ElementPattern<FileContent> createSuitableFilePattern() {
    return FileContentPattern.fileContent().withName(string().startsWith("my-config")).xmlWithRootTag(ROOT_TAG_NAME);
  }

  @Nullable
  private static MockFacetConfiguration detectConfiguration(final String fileName,
                                                           final Collection<MockFacetConfiguration> existentFacetConfigurations) {
    for (MockFacetConfiguration configuration : existentFacetConfigurations) {
      if (fileName.equals(configuration.getData())) {
        return null;
      }
    }
    return new MockFacetConfiguration(fileName);
  }
}
