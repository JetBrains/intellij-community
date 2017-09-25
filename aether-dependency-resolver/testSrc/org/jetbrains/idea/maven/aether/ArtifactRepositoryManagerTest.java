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

import java.io.File;
import java.util.Collection;

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
    Collection<File> files = myRepositoryManager.resolveDependency("junit", "junit", "4.12", true);
    assertSameElements(ContainerUtil.map(files, File::getName), "junit-4.12.jar", "hamcrest-core-1.3.jar");
  }

  public void testResolveNonTransitively() throws Exception {
    Collection<File> files = myRepositoryManager.resolveDependency("junit", "junit", "4.12", false);
    assertSameElements(ContainerUtil.map(files, File::getName), "junit-4.12.jar");
  }
}
