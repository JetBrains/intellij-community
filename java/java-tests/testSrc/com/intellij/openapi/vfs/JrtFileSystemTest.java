/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.impl.jrt.JrtFileSystem;
import com.intellij.testFramework.LightPlatformTestCase;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

public class JrtFileSystemTest {
  private static String ourJdkHome = System.getenv("JDK_19");

  @BeforeClass
  public static void setUpClass() {
    assumeTrue("skipped: java=" + SystemInfo.JAVA_VERSION, JrtFileSystem.isSupported());
    assumeTrue("skipped: JDK_19=" + ourJdkHome, ourJdkHome != null && JrtFileSystem.isModularJdk(ourJdkHome));

    LightPlatformTestCase.initApplication();
  }

  @Test
  public void testBasicOps() throws IOException {
    String url = VirtualFileManager.constructUrl(JrtFileSystem.PROTOCOL, FileUtil.toSystemIndependentName(ourJdkHome) + JrtFileSystem.SEPARATOR);
    VirtualFile root = VirtualFileManager.getInstance().findFileByUrl(url);
    assertNotNull(root);
    assertTrue(JrtFileSystem.isRoot(root));

    assumeNotNull(root.findChild("java"));
    assumeNotNull(root.findChild("javax"));

    VirtualFile object = root.findFileByRelativePath("java/lang/Object.class");
    assertNotNull(object);

    byte[] bytes = object.contentsToByteArray();
    assertTrue(bytes.length > 10);
    assertEquals(0xCAFEBABE, ByteBuffer.wrap(bytes).getInt());
  }
}
