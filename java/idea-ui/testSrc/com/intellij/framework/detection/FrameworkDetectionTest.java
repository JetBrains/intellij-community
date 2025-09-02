// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.framework.detection;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.mock.*;
import com.intellij.framework.detection.impl.FacetBasedDetectedFrameworkDescription;
import com.intellij.framework.detection.impl.FrameworkDetectionManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.testFramework.DynamicPluginTestUtilsKt;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;

import java.util.List;

public class FrameworkDetectionTest extends FrameworkDetectionTestCase {
  private VirtualFile myTestDir;
  private FrameworkDetectionManager myDetectionManager;

  public void testDetect() {
    final VirtualFile file = createFrameworkConfig("my-config.xml");
    final MockFacetConfiguration configuration = assertFrameworkDetectedIn(file);
    assertEquals("my-config.xml", configuration.getData());
  }

  public void testWrongExtension() {
    createFrameworkConfig("my-config.txt");
    assertNoFrameworksDetected();
  }

  public void testWrongName() {
    createFrameworkConfig("name.xml");
    assertNoFrameworksDetected();
  }

  public void testWrongContent() {
    createFile("my-config.xml", "<aaa />");
    assertNoFrameworksDetected();
  }

  public void testRevertDetected() {
    final VirtualFile file = createFrameworkConfig("my-config.xml");
    assertFrameworkDetectedIn(file);

    VfsTestUtil.clearContent(file);
    assertNoFrameworksDetected();
  }

  public void testDynamicDetector() {
    VirtualFile file = createFrameworkConfig("my-config.xml");
    assertNoFrameworksDetected();

    Disposer.register(getTestRootDisposable(), DynamicPluginTestUtilsKt.loadExtensionWithText(
      "<framework.detector implementation=\"" + MockFacetDetector.class.getName() + "\"/>",
      "com.intellij"));

    assertFrameworkDetectedIn(file);
  }

  public void testCreateFacet() {
    createFrameworkConfig("my-config.xml");
    setupFrameworks(detect());
    final MockFacet facet = assertOneElement(FacetManager.getInstance(myModule).getFacetsByType(MockFacetType.ID));
    assertTrue(facet.isInitialized());
    assertTrue(facet.isConfigured());
    assertEquals("my-config.xml", facet.getConfiguration().getData());
  }

  public void testCreateFacetAfterDetectionManually() {
    final VirtualFile file = createFrameworkConfig("my-config.xml");
    assertFrameworkDetectedIn(file);
    createMockFacet("my-config.xml");
    assertNoFrameworksDetected();
  }

  public void testDetectTwoFacets() {
    final VirtualFile file1 = createFrameworkConfig("my-config1.xml");
    final VirtualFile file2 = createFrameworkConfig("my-config2.xml");
    final List<? extends DetectedFrameworkDescription> descriptions = detect();
    assertEquals(2, descriptions.size());

    VfsTestUtil.clearContent(file1);
    assertFrameworkDetectedIn(file2);

    VfsTestUtil.clearContent(file2);
    assertNoFrameworksDetected();
  }

  public void testDetectFacetWithTwoConfigFiles() {
    final VirtualFile file1 = createFrameworkConfig("dir1/my-config.xml");
    final VirtualFile file2 = createFrameworkConfig("dir2/my-config.xml");
    assertFrameworkDetectedIn(file1, file2);

    VfsTestUtil.clearContent(file1);
    assertFrameworkDetectedIn(file2);

    delete(file2);
    assertNoFrameworksDetected();
  }

  public void testDetectFacetAndSubFacet() {
    createFrameworkConfig("my-config.xml");
    createFrameworkConfig("sub-my-config.xml");
    final List<? extends DetectedFrameworkDescription> descriptions = detect();
    assertEquals(2, descriptions.size());
    setupFrameworks(descriptions);
    final MockFacet facet = assertOneElement(FacetManager.getInstance(myModule).getFacetsByType(MockFacetType.ID));
    final Facet subFacet = assertOneElement(FacetManager.getInstance(myModule).getFacetsByType(MockSubFacetType.ID));
    assertSame(facet, subFacet.getUnderlyingFacet());
    assertEquals("my-config.xml", facet.getConfiguration().getData());
    assertEquals("sub-my-config.xml", ((MockFacetConfiguration)subFacet.getConfiguration()).getData());
  }

  public void testDoNotDetectSubFacetWithoutUnderlyingFacet() {
    createFrameworkConfig("sub-my-config.xml");
    assertNoFrameworksDetected();

    final VirtualFile file = createFrameworkConfig("my-config.xml");
    assertEquals(2, detect().size());

    VfsTestUtil.clearContent(file);
    assertNoFrameworksDetected();
  }

  public void testRevertSubFacetDetection() {
    final VirtualFile file1 = createFrameworkConfig("my-config.xml");
    final VirtualFile file2 = createFrameworkConfig("sub-my-config.xml");
    assertEquals(2, detect().size());
    VfsTestUtil.clearContent(file2);
    assertFrameworkDetectedIn(file1);
  }

  public void testDetectSubFacet() {
    final MockFacet facet = createMockFacet("my-config.xml");
    final VirtualFile file = createFrameworkConfig("sub-my-config.xml");
    final MockFacetConfiguration configuration = assertFrameworkDetectedIn(file);
    assertEquals("sub-my-config.xml", configuration.getData());
    setupFrameworks(detect());
    final Facet subFacet = assertOneElement(FacetManager.getInstance(myModule).getFacetsByType(MockSubFacetType.ID));
    assertSame(facet, subFacet.getUnderlyingFacet());
  }

  public void testTwoFacetsWithSubFacets() {
    createFrameworkConfig("my-config1.xml");
    createFrameworkConfig("my-config2.xml");
    createFrameworkConfig("sub-my-config1.xml");
    createFrameworkConfig("sub-my-config2.xml");
    assertEquals(4, detect().size());
  }

  public void testNotDetectInExcludedDir() {
    final VirtualFile dir = VfsTestUtil.createDir(myTestDir, "excluded");
    PsiTestUtil.addExcludedRoot(myModule, dir);
    createFrameworkConfig("excluded/my-config.xml");
    assertNoFrameworksDetected();
  }

  public void testDirectoryExcludedFromDetection() {
    DetectionExcludesConfiguration.getInstance(myProject).addExcludedFile(VfsTestUtil.createDir(myTestDir, "exc"), null);
    createFrameworkConfig("exc/my-config.xml");
    assertNoFrameworksDetected();
  }

  public void testFrameworkExcludedFromDetection() {
    DetectionExcludesConfiguration.getInstance(myProject).addExcludedFramework(FacetBasedFrameworkDetector.createFrameworkType(MockFacetType.getInstance()));
    createFrameworkConfig("my-config.xml");
    assertNoFrameworksDetected();
  }

  public void testExcludeFromDetectionHonorFrameworkType() {
    DetectionExcludesConfiguration.getInstance(myProject).addExcludedFramework(FacetBasedFrameworkDetector.createFrameworkType(MockSubFacetType.getInstance()));
    final VirtualFile file = createFrameworkConfig("my-config.xml");
    assertFrameworkDetectedIn(file);
  }

  private MockFacet createMockFacet(String fileName) {
    MockFacet facet = WriteAction.compute(() -> FacetManager.getInstance(myModule).addFacet(MockFacetType.getInstance(), "f", null));
    facet.getConfiguration().setData(fileName);
    return facet;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    VirtualFile testDir = getTempDir().createVirtualDir();
    myTestDir = WriteAction.compute(() -> {
      PsiTestUtil.addSourceContentToRoots(myModule, testDir);
      return testDir;
    });
    myDetectionManager = FrameworkDetectionManager.getInstance(myProject);
  }

  @Override
  public void tearDown() throws Exception {
    myDetectionManager = null;

    super.tearDown();
  }

  private void assertNoFrameworksDetected() {
    myDetectionManager.runDetection();
    assertEmpty(getDetectedFrameworks());
  }

  private List<? extends DetectedFrameworkDescription> getDetectedFrameworks() {
    return myDetectionManager.getDetectedFrameworks();
  }

  private MockFacetConfiguration assertFrameworkDetectedIn(VirtualFile... files) {
    final List<? extends DetectedFrameworkDescription> frameworks = detect();
    final DetectedFrameworkDescription description = assertOneElement(frameworks);
    assertSameElements(description.getRelatedFiles(), files);
    return assertInstanceOf(assertInstanceOf(description, FacetBasedDetectedFrameworkDescription.class).getConfiguration(), MockFacetConfiguration.class);
  }

  private List<? extends DetectedFrameworkDescription> detect() {
    myDetectionManager.runDetection();
    return getDetectedFrameworks();
  }

  private VirtualFile createFrameworkConfig(String filePath) {
    return createFile(filePath, MockFacetDetector.ROOT_TAG);
  }

  private VirtualFile createFile(String filePath, final String text) {
    return VfsTestUtil.createFile(myTestDir, filePath, text);
  }
}
