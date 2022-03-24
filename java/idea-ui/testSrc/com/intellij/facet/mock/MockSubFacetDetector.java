// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.mock;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.framework.detection.FacetBasedFrameworkDetector;
import com.intellij.framework.detection.FileContentPattern;
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.ElementPattern;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.patterns.StandardPatterns.string;

@InternalIgnoreDependencyViolation
public final class MockSubFacetDetector extends FacetBasedFrameworkDetector<Facet, MockFacetConfiguration> {
  public MockSubFacetDetector() {
    super("mock-sub-facet-detector");
  }

  @NotNull
  @Override
  public FacetType<Facet, MockFacetConfiguration> getFacetType() {
    return MockSubFacetType.getInstance();
  }

  @NotNull
  @Override
  public List<Pair<MockFacetConfiguration, Collection<VirtualFile>>> createConfigurations(@NotNull Collection<? extends VirtualFile> files,
                                                                                          @NotNull Collection<? extends MockFacetConfiguration> existentFacetConfigurations) {
    return MockFacetDetector.doDetect(files, existentFacetConfigurations);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return StdFileTypes.XML;
  }

  @Override
  public boolean isSuitableUnderlyingFacetConfiguration(FacetConfiguration underlying,
                                                        MockFacetConfiguration configuration,
                                                        Set<? extends VirtualFile> files) {
    return underlying instanceof MockFacetConfiguration && ("sub-" + ((MockFacetConfiguration)underlying).getData()).equals(configuration.getData());
  }

  @NotNull
  @Override
  public ElementPattern<FileContent> createSuitableFilePattern() {
    return FileContentPattern.fileContent()
      .withName(string().startsWith("sub-my-config"))
      .xmlWithRootTag(MockFacetDetector.ROOT_TAG_NAME);
  }
}
