/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.framework.detection;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.mock.MockFacet;
import com.intellij.facet.mock.MockFacetConfiguration;
import com.intellij.facet.mock.MockFacetType;
import com.intellij.facet.mock.MockSubFacetType;
import com.intellij.framework.detection.impl.FrameworkDetectionProcessor;
import com.intellij.ide.util.importProject.FrameworkDetectionInWizardContext;
import com.intellij.ide.util.importProject.ModuleDescriptor;
import com.intellij.ide.util.projectWizard.importSources.JavaModuleSourceRoot;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertNotEquals;

public class FrameworkDetectionInWizardTest extends FrameworkDetectionTestCase {

  public void testDetectFacet() {
    doDetect();
    MockFacet facet = assertOneElement(getFacets());
    assertNull(facet.getUnderlyingFacet());
    assertEquals("my-config1.xml", facet.getConfiguration().getData());
  }

  private Collection<MockFacet> getFacets() {
    return FacetManager.getInstance(myModule).getFacetsByType(MockFacetType.ID);
  }

  public void testDetectSubFacet() {
    doDetect();
    final List<MockFacet> facets = new ArrayList<>(getFacets());
    assertEquals(2, facets.size());
    assertNotEquals(facets.get(0).getName(), facets.get(1).getName());

    final Facet<?> subFacet = assertOneElement(FacetManager.getInstance(myModule).getFacetsByType(MockSubFacetType.ID));
    final Facet<?> parentFacet = subFacet.getUnderlyingFacet();
    assertNotNull(parentFacet);
    assertTrue(facets.contains(parentFacet));

    assertEquals("sub-my-config2.xml", ((MockFacetConfiguration)subFacet.getConfiguration()).getData());
  }

  private void doDetect() {
    final String path = PathManagerEx.getTestDataPath() + File.separator + "facet" + File.separator + "detection" + File.separator + getTestName(true);
    final File root = new File(path);
    FrameworkDetectionProcessor processor = new FrameworkDetectionProcessor(new MockProgressIndicator(), new FrameworkDetectionInWizardContext() {
      @Override
      protected List<ModuleDescriptor> getModuleDescriptors() {
        final ModuleDescriptor descriptor = new ModuleDescriptor(root, JavaModuleType.getModuleType(), new JavaModuleSourceRoot(root, null, "java"));
        descriptor.setName(myModule.getName());
        return Collections.singletonList(descriptor);
      }

      @NotNull
      @Override
      protected String getContentPath() {
        return FileUtil.toSystemIndependentName(path);
      }
    });
    final List<? extends DetectedFrameworkDescription> descriptions = processor.processRoots(Collections.singletonList(root));
    setupFrameworks(descriptions);
  }
}
