// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.openapi.vfs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl;
import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

import static com.intellij.openapi.vfs.impl.VirtualFilePointerTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * @author yole
 */
public class JrtVirtualFilePointerTest extends LightPlatformCodeInsightFixtureTestCase {

  private VirtualFilePointerManagerImpl myVirtualFilePointerManager;
  private Disposable myDisposable;

  public void setUp() throws Exception {
    super.setUp();
    myVirtualFilePointerManager = (VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance();
    myDisposable = Disposer.newDisposable();
  }

  public void tearDown() throws Exception {
    try {
      Disposer.dispose(myDisposable);
    }
    finally {
      super.tearDown();
    }
  }

  public void testJrt() throws Exception {
    assumeTrue(SystemInfo.isUnix);
    final File tempDir = FileUtil.createTempDirectory("jrt", "");
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir);
    JrtFileSystemTest.setupJrtFileSystem(tempDir);

    VirtualFile vTemp = PlatformTestUtil.notNull(refreshAndFindFile(tempDir));
    assertThat(vTemp.isValid()).isTrue();

    final VirtualFilePointer[] pointersToWatch = new VirtualFilePointer[2];
    final VirtualFilePointerListener listener = new VirtualFilePointerListener() {
      @Override
      public void beforeValidityChanged(@NotNull VirtualFilePointer[] pointers) {
        verifyPointersInCorrectState(pointersToWatch);
      }

      @Override
      public void validityChanged(@NotNull VirtualFilePointer[] pointers) {
        verifyPointersInCorrectState(pointersToWatch);
      }
    };
    final VirtualFilePointer jrtParentPointer = createPointerByFile(tempDir, listener);
    final String jrtUrl = VirtualFileManager.constructUrl(JrtFileSystem.PROTOCOL, tempDir + JrtFileSystem.SEPARATOR);
    final VirtualFilePointer jrtPointer = myVirtualFilePointerManager.create(jrtUrl, myDisposable, listener);
    pointersToWatch[0] = jrtParentPointer;
    pointersToWatch[1] = jrtPointer;
    assertThat(jrtParentPointer.isValid()).isTrue();
    assertThat(jrtPointer.isValid()).isTrue();

    assertThat(FileUtil.delete(tempDir)).isTrue();
    refreshVFS();

    verifyPointersInCorrectState(pointersToWatch);
    assertThat(jrtParentPointer.isValid()).isFalse();
    assertThat(jrtPointer.isValid()).isFalse();
    UIUtil.dispatchAllInvocationEvents();

    FileUtil.createDirectory(tempDir);
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir);
    JrtFileSystemTest.setupJrtFileSystem(tempDir);

    refreshVFS();
    verifyPointersInCorrectState(pointersToWatch);
    assertThat(jrtParentPointer.isValid()).isTrue();
    assertThat(jrtPointer.isValid()).isTrue();
    UIUtil.dispatchAllInvocationEvents();

    assertThat(FileUtil.delete(tempDir)).isTrue();
    refreshVFS();
    UIUtil.dispatchAllInvocationEvents();

    verifyPointersInCorrectState(pointersToWatch);
    assertThat(jrtParentPointer.isValid()).isFalse();
    assertThat(jrtPointer.isValid()).isFalse();
    UIUtil.dispatchAllInvocationEvents();
  }

  @NotNull
  private VirtualFilePointer createPointerByFile(@NotNull File file, @Nullable VirtualFilePointerListener fileListener) throws IOException {
    final String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, file.getCanonicalPath().replace(File.separatorChar, '/'));
    final VirtualFile vFile = refreshAndFind(url);
    return vFile == null
           ? myVirtualFilePointerManager.create(url, myDisposable, fileListener)
           : myVirtualFilePointerManager.create(vFile, myDisposable, fileListener);
  }
}
