/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.jdom.Element;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactSerializer;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.jps.model.serialization.JpsProjectSerializationTest.SAMPLE_PROJECT_PATH;

/**
 * @author nik
 */
public class JpsArtifactSerializationTest extends JpsSerializationTestCase {
  public void testLoadProject() {
    loadProject(SAMPLE_PROJECT_PATH);
    List<JpsArtifact> artifacts = getService().getSortedArtifacts(myProject);
    assertEquals(2, artifacts.size());
    assertEquals("dir", artifacts.get(0).getName());
    assertEquals("jar", artifacts.get(1).getName());
  }

  public void testSaveProject() {
    loadProject(SAMPLE_PROJECT_PATH);
    File[] artifactFiles = new File(getTestDataFileAbsolutePath(SAMPLE_PROJECT_PATH + "/.idea/artifacts")).listFiles();
    assertNotNull(artifactFiles);
    for (File file : artifactFiles) {
      JpsArtifact artifact = getService().createReference(FileUtil.getNameWithoutExtension(file)).asExternal(myModel).resolve();
      assertNotNull(artifact);
      doTestSaveArtifact(artifact, file);
    }
  }

  private void doTestSaveArtifact(JpsArtifact artifact, File expectedFile) {
    try {
      Element actual = new Element("component").setAttribute("name", "ArtifactManager");
      JpsArtifactSerializer.saveArtifact(artifact, actual);
      JpsMacroExpander
        expander = JpsProjectLoader.createProjectMacroExpander(Collections.<String, String>emptyMap(), new File(getTestDataFileAbsolutePath(SAMPLE_PROJECT_PATH)));
      Element expected = JpsLoaderBase.loadRootElement(expectedFile, expander);
      PlatformTestUtil.assertElementsEqual(expected, actual);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static JpsArtifactService getService() {
    return JpsArtifactService.getInstance();
  }
}
