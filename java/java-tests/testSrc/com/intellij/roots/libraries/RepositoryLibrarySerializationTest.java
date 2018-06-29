// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots.libraries;

import com.intellij.jarRepository.RepositoryLibraryType;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryTableBase;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.roots.ModuleRootManagerTestCase;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;

import java.io.IOException;

public class RepositoryLibrarySerializationTest extends ModuleRootManagerTestCase {
  public void testPlain() throws JDOMException, IOException {
    RepositoryLibraryProperties properties = loadLibrary("plain");
    assertEquals("junit", properties.getGroupId());
    assertEquals("junit", properties.getArtifactId());
    assertEquals("3.8.1", properties.getVersion());
    assertTrue(properties.isIncludeTransitiveDependencies());
    assertEmpty(properties.getExcludedDependencies());
  }

  public void testWithoutTransitiveDependencies() throws JDOMException, IOException {
    RepositoryLibraryProperties properties = loadLibrary("without-transitive-dependencies");
    assertFalse(properties.isIncludeTransitiveDependencies());
    assertEmpty(properties.getExcludedDependencies());
  }

  public void testWithExcludedDependencies() throws JDOMException, IOException {
    RepositoryLibraryProperties properties = loadLibrary("with-excluded-dependencies");
    assertTrue(properties.isIncludeTransitiveDependencies());
    assertSameElements(properties.getExcludedDependencies(), "org.apache.httpcomponents:httpclient");
  }

  @NotNull
  private RepositoryLibraryProperties loadLibrary(String name) throws JDOMException, IOException {
    String libraryPath = "jps/model-serialization/testData/repositoryLibraries/.idea/libraries/" + name + ".xml";
    Element element = JDOMUtil.load(PathManagerEx.findFileUnderCommunityHome(libraryPath));
    LibraryTableBase libraryTable = (LibraryTableBase)LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    libraryTable.loadState(element);
    LibraryEx library = (LibraryEx)libraryTable.getLibraryByName(name);
    assertNotNull(library);
    assertSame(RepositoryLibraryType.REPOSITORY_LIBRARY_KIND, library.getKind());
    RepositoryLibraryProperties properties = (RepositoryLibraryProperties)library.getProperties();
    assertNotNull(properties);
    return properties;
  }
}
