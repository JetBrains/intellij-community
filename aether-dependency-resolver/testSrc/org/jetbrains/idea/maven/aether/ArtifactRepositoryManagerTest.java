/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.aether;

import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.eclipse.aether.artifact.Artifact;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * @author nik
 */
public class ArtifactRepositoryManagerTest extends UsefulTestCase {
  private ArtifactRepositoryManager myRepositoryManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final File localRepo = new File(SystemProperties.getUserHome(), ".m2/repository");
    myRepositoryManager = new ArtifactRepositoryManager(localRepo);
  }

  public void testResolveTransitively() throws Exception {
    Collection<File> files = myRepositoryManager.resolveDependency("junit", "junit", "4.12", true, Collections.emptyList());
    assertFileNames(files, "junit-4.12.jar", "hamcrest-core-1.3.jar");
  }

  public void testResolveNonTransitively() throws Exception {
    Collection<File> files = myRepositoryManager.resolveDependency("junit", "junit", "4.12", false, Collections.emptyList());
    assertFileNames(files, "junit-4.12.jar");
  }

  public void testExcludeDirectDependency() throws Exception {
    Collection<File> all = myRepositoryManager.resolveDependency("junit", "junit", "4.12", true, Collections.singletonList("org.hamcrest:wrong"));
    assertFileNames(all, "junit-4.12.jar", "hamcrest-core-1.3.jar");
    Collection<File> excluded = myRepositoryManager.resolveDependency("junit", "junit", "4.12", true, Collections.singletonList("org.hamcrest:hamcrest-core"));
    assertFileNames(excluded, "junit-4.12.jar");
  }

  public void testExcludeDependenciesTransitively() throws Exception {
    Collection<File> all = myRepositoryManager.resolveDependency("org.apache.httpcomponents", "fluent-hc", "4.5.5", true, Collections.emptyList());
    assertFileNames(all, "fluent-hc-4.5.5.jar", "httpclient-4.5.5.jar", "httpcore-4.4.9.jar", "commons-logging-1.2.jar", "commons-codec-1.10.jar");
    Collection<File> excluded = myRepositoryManager.resolveDependency("org.apache.httpcomponents", "fluent-hc", "4.5.5", true, Collections.singletonList("org.apache.httpcomponents:httpclient"));
    assertFileNames(excluded, "fluent-hc-4.5.5.jar", "commons-logging-1.2.jar");
  }

  public void testResolveRuntimeDependencies() throws Exception {
    Collection<File> files = myRepositoryManager.resolveDependency("com.netflix.feign", "feign-jackson", "8.18.0", true,
                                                                   Collections.emptyList());
    assertContainsElements(ContainerUtil.map(files, File::getName), "feign-core-8.18.0.jar");
  }

  public void testCollectDependencies() throws Exception {
    ArtifactDependencyNode result = myRepositoryManager.collectDependencies("org.apache.httpcomponents", "fluent-hc", "4.5.5");
    assertNotNull(result);
    assertCoordinates(result.getArtifact(), "org.apache.httpcomponents", "fluent-hc", "4.5.5");
    assertEquals(2, result.getDependencies().size());
    ArtifactDependencyNode first = result.getDependencies().get(0);
    ArtifactDependencyNode second = result.getDependencies().get(1);
    assertCoordinates(first.getArtifact(), "org.apache.httpcomponents", "httpclient", "4.5.5");
    assertCoordinates(second.getArtifact(), "commons-logging", "commons-logging", "1.2");
    assertEquals(2, first.getDependencies().size());
    assertCoordinates(first.getDependencies().get(0).getArtifact(), "org.apache.httpcomponents", "httpcore", "4.4.9");
    assertCoordinates(first.getDependencies().get(1).getArtifact(), "commons-codec", "commons-codec", "1.10");
  }

  private static void assertCoordinates(Artifact artifact, String groupId, String artifactId, String version) {
    assertEquals(groupId, artifact.getGroupId());
    assertEquals(artifactId, artifact.getArtifactId());
    assertEquals(version, artifact.getVersion());
  }

  private static void assertFileNames(Collection<File> files, String... expectedNames) {
    assertSameElements(ContainerUtil.map(files, File::getName), expectedNames);
  }
}
