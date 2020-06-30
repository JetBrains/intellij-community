// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jdom.Element;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.testFramework.assertions.Assertions.assertThat;
import static org.jetbrains.jps.model.serialization.JpsProjectSerializationTest.SAMPLE_PROJECT_PATH;

public class JpsArtifactSerializationTest extends JpsSerializationTestCase {
  public void testLoadProject() {
    loadProject(SAMPLE_PROJECT_PATH);
    List<JpsArtifact> artifacts = getService().getSortedArtifacts(myProject);
    assertEquals(2, artifacts.size());
    assertEquals("dir", artifacts.get(0).getName());
    assertEquals("jar", artifacts.get(1).getName());
  }

  public void testSaveProject() throws IOException {
    loadProject(SAMPLE_PROJECT_PATH);
    try (Stream<Path> artifacts = Files.list(getTestDataAbsoluteFile(SAMPLE_PROJECT_PATH + "/.idea/artifacts"))) {
      List<Path> artifactFiles = artifacts.collect(Collectors.toList());
      assertNotNull(artifactFiles);
      for (Path file : artifactFiles) {
        JpsArtifact artifact = getService().createReference(
          FileUtilRt.getNameWithoutExtension(file.getFileName().toString())).asExternal(myModel).resolve();
        assertNotNull(artifact);
        doTestSaveArtifact(artifact, file);
      }
    }
  }

  private void doTestSaveArtifact(JpsArtifact artifact, Path expectedFile) {
    Element actual = new Element("component").setAttribute("name", "ArtifactManager");
    JpsArtifactSerializer.saveArtifact(artifact, actual);
    JpsMacroExpander
      expander = JpsProjectLoader.createProjectMacroExpander(Collections.emptyMap(), getTestDataAbsoluteFile(SAMPLE_PROJECT_PATH));
    assertThat(actual).isEqualTo(JpsLoaderBase.loadRootElement(expectedFile, expander));
  }

  private static JpsArtifactService getService() {
    return JpsArtifactService.getInstance();
  }
}
