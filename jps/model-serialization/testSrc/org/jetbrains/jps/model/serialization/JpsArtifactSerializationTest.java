// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization;

import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;

import java.util.List;

import static org.jetbrains.jps.model.serialization.JpsProjectSerializationTest.SAMPLE_PROJECT_PATH;

public class JpsArtifactSerializationTest extends JpsSerializationTestCase {
  public void testLoadProject() {
    loadProject(SAMPLE_PROJECT_PATH);
    List<JpsArtifact> artifacts = getService().getSortedArtifacts(myProject);
    assertEquals(2, artifacts.size());
    assertEquals("dir", artifacts.get(0).getName());
    assertEquals("jar", artifacts.get(1).getName());
  }

  private static JpsArtifactService getService() {
    return JpsArtifactService.getInstance();
  }
}
