/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.testFramework.IdeaTestCase;

import java.io.IOException;

import static com.intellij.testFramework.PlatformTestUtil.assertPathsEqual;

public class JarFileSystemTest extends IdeaTestCase {
  public void testFindFile() throws IOException {
    String rtJarPath = getJdkRtPath("src.zip");

    VirtualFile jarRoot = findByPath(rtJarPath + JarFileSystem.JAR_SEPARATOR);
    assertTrue(jarRoot.isDirectory());

    VirtualFile file2 = findByPath(rtJarPath + JarFileSystem.JAR_SEPARATOR + "java");
    assertTrue(file2.isDirectory());

    VirtualFile file3 = jarRoot.findChild("java");
    assertEquals(file2, file3);

    VirtualFile file4 = findByPath(rtJarPath + JarFileSystem.JAR_SEPARATOR + "java/lang/Object.java");
    assertTrue(!file4.isDirectory());

    byte[] bytes = file4.contentsToByteArray();
    assertNotNull(bytes);
    assertTrue(bytes.length > 10);
  }

  public void testMetaInf() {
    String rtJarPath = getJdkRtPath("jre/lib/rt.jar");

    VirtualFile jarRoot = findByPath(rtJarPath + JarFileSystem.JAR_SEPARATOR);
    assertTrue(jarRoot.isDirectory());

    VirtualFile metaInf = jarRoot.findChild("META-INF");
    assertNotNull(metaInf);

    VirtualFile[] children = metaInf.getChildren();
    assertEquals(1, children.length);
  }

  private String getJdkRtPath(String relativePath) {
    Sdk jdk = ModuleRootManager.getInstance(myModule).getSdk();
    assertNotNull(jdk);
    VirtualFile jdkHome = jdk.getHomeDirectory();
    assertNotNull(jdkHome);
    return jdkHome.getPath() + "/" + relativePath;
  }

  private static VirtualFile findByPath(String path) {
    VirtualFile file = JarFileSystem.getInstance().findFileByPath(path);
    assertNotNull(file);
    assertPathsEqual(path, file.getPath());
    return file;
  }
}
