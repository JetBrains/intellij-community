// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.openapi.vfs;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.After;
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

  private final Disposable myDisposable = Disposer.newDisposable();
  private Path myTestData;
  private Path myTempPath;
  private VirtualFile myRoot;

  @Before
  public void setUp() throws IOException {
    myTestData = Paths.get(JavaTestUtil.getJavaTestDataPath(), "jrt");
    myTempPath = myTempDir.newFolder("jrt").toPath();

    setupJrtFileSystem();
    myRoot = findRoot(myTempPath.toString());
    assertThat(myRoot).isNotNull();
    assertThat(JrtFileSystem.isRoot(myRoot)).isTrue();
  }

  @After
  public void tearDown() {
    Disposer.dispose(myDisposable);
  }

  private void setupJrtFileSystem() throws IOException {
    Files.createDirectories(myTempPath);
    Files.write(myTempPath.resolve("release"), "JAVA_VERSION=9\n".getBytes(CharsetToolkit.UTF8_CHARSET));
    Path lib = Files.createDirectory(myTempPath.resolve("lib"));
    Files.copy(myTestData.resolve("jrt-fs.jar"), lib.resolve("jrt-fs.jar"));
    Files.copy(myTestData.resolve("image1"), lib.resolve("modules"));
    LocalFileSystem.getInstance().refreshAndFindFileByPath(myTempPath.toString());
  }

  @Test
  public void nonRoot() {
    VirtualFile root = findRoot(JavaTestUtil.getJavaTestDataPath());
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
    VirtualFile local = LocalFileSystem.getInstance().findFileByPath(myTempPath.toString());
    assertThat(local).isNotNull();

    Path modules = myTempPath.resolve("lib/modules");
    Files.move(modules, myTempPath.resolve("lib/modules.bak"), StandardCopyOption.ATOMIC_MOVE);
    Files.copy(myTestData.resolve("image2"), modules);
    Files.write(myTempPath.resolve("release"), "JAVA_VERSION=9.0.1\n".getBytes(CharsetToolkit.UTF8_CHARSET));
    List<VFileEvent> events = VfsTestUtil.getEvents(() -> local.refresh(false, true));
    assertThat(childNames(myRoot)).describedAs("events=" + events).containsExactlyInAnyOrder("java.base", "test.a", "test.b");

    if (SystemInfo.isUnix) {
      assertThat(FileUtil.delete(myTempPath.toFile())).isTrue();
      events = VfsTestUtil.getEvents(() -> local.refresh(false, true));
      assertThat(myRoot.isValid()).describedAs("events=" + events).isFalse();
    }
  }

  @Test
  public void filePointers() throws IOException {
    VirtualFile vTemp = LocalFileSystem.getInstance().refreshAndFindFileByPath(myTempPath.toString());
    assertThat(vTemp).isNotNull();
    VirtualFilePointerManager manager = VirtualFilePointerManager.getInstance();
    VirtualFilePointer[] pointers = {manager.create(vTemp, myDisposable, null), manager.create(myRoot, myDisposable, null)};
    assertPointers(pointers, true);

    if (SystemInfo.isUnix) {
      VirtualFile testRoot = vTemp.getParent();

      assertThat(FileUtil.delete(myTempPath.toFile())).isTrue();
      testRoot.refresh(false, true);
      assertPointers(pointers, false);

      setupJrtFileSystem();
      testRoot.refresh(false, true);
      assertPointers(pointers, true);

      assertThat(FileUtil.delete(myTempPath.toFile())).isTrue();
      testRoot.refresh(false, true);
      assertPointers(pointers, false);
    }
  }

  private static VirtualFile findRoot(String path) {
    String url = VirtualFileManager.constructUrl(JrtFileSystem.PROTOCOL, path + JrtFileSystem.SEPARATOR);
    return VirtualFileManager.getInstance().findFileByUrl(url);
  }

  private static List<String> childNames(VirtualFile dir) {
    return Stream.of(dir.getChildren()).map(VirtualFile::getName).collect(Collectors.toList());
  }

  private static void assertPointers(VirtualFilePointer[] pointers, boolean valid) {
    assertThat(pointers).allMatch(p -> p.isValid() == valid);
    assertThat(pointers).allMatch(p -> p.getFile() == null || p.getFile().isValid());
  }
}