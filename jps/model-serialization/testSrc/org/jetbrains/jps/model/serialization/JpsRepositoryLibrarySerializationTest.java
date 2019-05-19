// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;

public class JpsRepositoryLibrarySerializationTest extends JpsSerializationTestCase {
  public void testPlain() {
    JpsMavenRepositoryLibraryDescriptor properties = loadLibrary("plain");
    assertEquals("junit", properties.getGroupId());
    assertEquals("junit", properties.getArtifactId());
    assertEquals("3.8.1", properties.getVersion());
    assertTrue(properties.isIncludeTransitiveDependencies());
    assertEmpty(properties.getExcludedDependencies());
  }

  public void testWithoutTransitiveDependencies() {
    JpsMavenRepositoryLibraryDescriptor properties = loadLibrary("without-transitive-dependencies");
    assertFalse(properties.isIncludeTransitiveDependencies());
    assertEmpty(properties.getExcludedDependencies());
  }

  public void testWithExcludedDependencies() {
    JpsMavenRepositoryLibraryDescriptor properties = loadLibrary("with-excluded-dependencies");
    assertTrue(properties.isIncludeTransitiveDependencies());
    assertSameElements(properties.getExcludedDependencies(), "org.apache.httpcomponents:httpclient");
  }

  @NotNull
  private JpsMavenRepositoryLibraryDescriptor loadLibrary(String name) {
    loadProject("/jps/model-serialization/testData/repositoryLibraries");
    JpsLibrary library = myProject.getLibraryCollection().findLibrary(name);
    assertNotNull(library);
    assertSame(JpsRepositoryLibraryType.INSTANCE, library.getType());
    JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>> typed = library.asTyped(JpsRepositoryLibraryType.INSTANCE);
    assertNotNull(typed);
    return typed.getProperties().getData();
  }
}
