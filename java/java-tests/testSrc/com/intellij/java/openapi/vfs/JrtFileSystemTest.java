// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.openapi.vfs;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.jrt.JrtFileSystemImpl;
import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.UriUtil;
import com.intellij.util.containers.ContainerUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

public class JrtFileSystemTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory tempDir = new TempDirectory();

  private final Disposable myDisposable = Disposer.newDisposable();
  private Path myTestData;
  private Path myJrtPath;
  private VirtualFile myRoot; // in jrt:// FS

  @Before
  public void setUp() throws IOException {
    myTestData = Paths.get(JavaTestUtil.getJavaTestDataPath(), "jrt");
    myJrtPath = tempDir.newDirectory("jrt").toPath();

    setupJrtFileSystem();
    myRoot = findRoot(myJrtPath.toString());
    assertNotNull(myRoot);
    assertTrue(JrtFileSystem.isRoot(myRoot));
  }

  @After
  public void tearDown() {
    Disposer.dispose(myDisposable);
    releaseJrtFileSystem();
    myRoot = null;
  }

  private void setupJrtFileSystem() throws IOException {
    Files.createDirectories(myJrtPath);
    Files.writeString(myJrtPath.resolve("release"), "JAVA_VERSION=9\n");
    Path lib = Files.createDirectory(myJrtPath.resolve("lib"));
    Files.copy(myTestData.resolve("jrt-fs.jar"), lib.resolve("jrt-fs.jar"));
    Files.copy(myTestData.resolve("image1"), lib.resolve("modules"));
    LocalFileSystem.getInstance().refreshAndFindFileByPath(myJrtPath.toString());
  }

  @SuppressWarnings("CallToSystemGC")
  private void releaseJrtFileSystem() {
    ((JrtFileSystemImpl)myRoot.getFileSystem()).release(FileUtil.toSystemIndependentName(myJrtPath.toString()));
    System.gc();
  }

  @Test
  public void nonRoot() {
    VirtualFile root = findRoot(JavaTestUtil.getJavaTestDataPath());
    assertNull(root);
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
    VirtualFile local = LocalFileSystem.getInstance().refreshAndFindFileByPath(myJrtPath.toString());
    assertThat(local).isNotNull();

    Path modules = myJrtPath.resolve("lib/modules");
    Files.move(modules, myJrtPath.resolve("lib/modules.bak"), StandardCopyOption.ATOMIC_MOVE);
    Files.copy(myTestData.resolve("image2"), modules);
    Files.writeString(myJrtPath.resolve("release"), "JAVA_VERSION=9.0.1\n");
    List<VFileEvent> events = VfsTestUtil.getEvents(() -> local.refresh(false, true));
    assertThat(childNames(myRoot)).describedAs("events=" + events).containsExactlyInAnyOrder("java.base", "test.a", "test.b");

    if (SystemInfo.isUnix) {
      FileUtil.delete(myJrtPath);
      events = VfsTestUtil.getEvents(() -> local.refresh(false, true));
      assertThat(myRoot.isValid()).describedAs("events=" + events).isFalse();
    }
  }

  @Test
  public void filePointers() throws IOException {
    VirtualFile local = LocalFileSystem.getInstance().refreshAndFindFileByPath(myJrtPath.toString());
    assertThat(local).isNotNull();
    VirtualFilePointerManager manager = VirtualFilePointerManager.getInstance();
    VirtualFilePointer[] pointers = {manager.create(local, myDisposable, null), manager.create(myRoot, myDisposable, null)};
    assertPointers(pointers, true);

    if (SystemInfo.isUnix) {
      VirtualFile testRoot = local.getParent();

      FileUtil.delete(myJrtPath);
      testRoot.refresh(false, true);
      assertPointers(pointers, false);

      setupJrtFileSystem();
      testRoot.refresh(false, true);
      assertPointers(pointers, true);
    }
  }

  private static VirtualFile findRoot(String path) {
    String url = VirtualFileManager.constructUrl(JrtFileSystem.PROTOCOL, path + JrtFileSystem.SEPARATOR);
    return VirtualFileManager.getInstance().findFileByUrl(url);
  }

  private static List<String> childNames(VirtualFile dir) {
    return ContainerUtil.map(dir.getChildren(), VirtualFile::getName);
  }

  private static void assertPointers(VirtualFilePointer[] pointers, boolean valid) {
    assertThat(pointers).allMatch(p -> p.isValid() == valid);
    assertThat(pointers).allMatch(p -> p.getFile() == null || p.getFile().isValid());
  }

  @Test
  public void testJDKInstalledIntoDiskRootUnderWindowsDoesntCauseHorribleThings() {
    IoTestUtil.assumeWindows();

    IoTestUtil.performTestOnWindowsSubst(myJrtPath.toString(), substRoot -> {
      VfsRootAccess.allowRootAccess(myDisposable, substRoot.getPath());

      String substedUrl = "jrt://" + UriUtil.trimTrailingSlashes(FileUtil.toSystemIndependentName(substRoot.getPath())) + "/!/java.base";
      VirtualFilePointer pointer = VirtualFilePointerManager.getInstance().create(substedUrl, myDisposable, null);
      assertTrue(pointer.isValid());
      VirtualFile file = pointer.getFile();
      assertNotNull(file);
      assertTrue(file.getFileSystem() instanceof JrtFileSystem);
    });
  }
}