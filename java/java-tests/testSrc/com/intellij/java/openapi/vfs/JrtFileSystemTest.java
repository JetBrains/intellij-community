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
package com.intellij.java.openapi.vfs;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class JrtFileSystemTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory myTempDir = new TempDirectory();

  private Path myTestData;
  private Path myTempPath;
  private VirtualFile myRoot;

  @Before
  public void setUp() throws IOException {
    myTestData = Paths.get(JavaTestUtil.getJavaTestDataPath(), "jrt");
    myTempPath = myTempDir.getRoot().toPath();
    Files.write(myTempPath.resolve("release"), "JAVA_VERSION=9\n".getBytes(CharsetToolkit.UTF8_CHARSET));
    Path lib = Files.createDirectory(myTempPath.resolve("lib"));
    Files.copy(myTestData.resolve("jrt-fs.jar"), lib.resolve("jrt-fs.jar"));
    Files.copy(myTestData.resolve("image1"), lib.resolve("modules"));
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myTempDir.getRoot());

    String url = VirtualFileManager.constructUrl(JrtFileSystem.PROTOCOL, myTempDir.getRoot() + JrtFileSystem.SEPARATOR);
    myRoot = VirtualFileManager.getInstance().findFileByUrl(url);
    assertThat(myRoot).isNotNull();
    assertThat(JrtFileSystem.isRoot(myRoot)).isTrue();
  }

  @Test
  public void nonRoot() {
    String url = VirtualFileManager.constructUrl(JrtFileSystem.PROTOCOL, JavaTestUtil.getJavaTestDataPath() + JrtFileSystem.SEPARATOR);
    VirtualFile root = VirtualFileManager.getInstance().findFileByUrl(url);
    assertThat(root).isNull();
  }

  @Test
  public void basicOps() throws IOException {
    assertThat(childNames(myRoot)).containsExactlyInAnyOrder("java.base", "test.a");

    VirtualFile moduleRoot = myRoot.findChild("test.a");
    assertThat(moduleRoot).isNotNull();
    assertThat(JrtFileSystem.isModuleRoot(moduleRoot)).isTrue();
    assertThat(childNames(moduleRoot)).containsExactlyInAnyOrder("pkg_a", "module-info.class");

    VirtualFile classFile = moduleRoot.findFileByRelativePath("pkg_a/A.class");
    assertThat(classFile).isNotNull();

    byte[] bytes = classFile.contentsToByteArray();
    assertThat(bytes.length).isGreaterThan(10);
    assertThat(ByteBuffer.wrap(bytes).getInt()).isEqualTo(0xCAFEBABE);
  }

  @Test
  public void refresh() throws IOException {
    assertThat(childNames(myRoot)).containsExactlyInAnyOrder("java.base", "test.a");

    Path modules = myTempPath.resolve("lib/modules");
    Files.move(modules, myTempPath.resolve("lib/modules.bak"), StandardCopyOption.ATOMIC_MOVE);
    Files.copy(myTestData.resolve("image2"), modules);
    Files.write(myTempPath.resolve("release"), "JAVA_VERSION=9.0.1\n".getBytes(CharsetToolkit.UTF8_CHARSET));

    VirtualFile local = LocalFileSystem.getInstance().findFileByIoFile(myTempDir.getRoot());
    assertThat(local).isNotNull();
    List<VFileEvent> events = VfsTestUtil.getEvents(() -> local.refresh(false, true));

    assertThat(childNames(myRoot)).describedAs("events=" + events).containsExactlyInAnyOrder("java.base", "test.a", "test.b");
  }

  private static List<String> childNames(VirtualFile dir) {
    return Stream.of(dir.getChildren()).map(VirtualFile::getName).collect(Collectors.toList());
  }
}