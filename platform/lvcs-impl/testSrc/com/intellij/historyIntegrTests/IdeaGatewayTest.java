/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.historyIntegrTests;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

public class IdeaGatewayTest extends IntegrationTestCase {
  public void testFindingFile() throws Exception {
    assertSame(root, gateway.findVirtualFile(root.getPath()));
    assertNull(gateway.findVirtualFile(root.getPath() + "/nonexistent"));
  }

  public void testGettingDirectory() throws Exception {
    assertSame(root, gateway.findOrCreateFileSafely(root.getPath(), true));
  }

  public void testCreatingDirectory() throws Exception {
    String subSubDirPath = root.getPath() + "/subDir/subSubDir";

    assertFalse(new File(subSubDirPath).exists());
    VirtualFile subDir = gateway.findOrCreateFileSafely(subSubDirPath, true);

    assertNotNull(subDir);
    assertEquals(subSubDirPath, subDir.getPath());

    assertTrue(new File(subSubDirPath).exists());
  }

  public void testCreatingDirectoryWhenSuchFileExists() throws Exception {
    String subSubDirPath = root.getPath() + "/subDir/subSubDir";

    assertFalse(new File(subSubDirPath).exists());
    root.createChildData(this, "subDir");

    VirtualFile subDir = gateway.findOrCreateFileSafely(subSubDirPath, true);

    assertNotNull(subDir);
    assertEquals(subSubDirPath, subDir.getPath());

    assertTrue(new File(subSubDirPath).exists());
  }
}
