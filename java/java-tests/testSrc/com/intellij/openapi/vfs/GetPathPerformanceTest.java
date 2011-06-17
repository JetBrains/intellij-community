package com.intellij.openapi.vfs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;

import java.io.File;
import java.io.IOException;

public class GetPathPerformanceTest extends LightPlatformTestCase {
  public void testGetPath() throws IOException, InterruptedException {
    final File dir = FileUtil.createTempDirectory("GetPath","");
    disposeOnTearDown(new Disposable() {
      @Override
      public void dispose() {
        FileUtil.delete(dir);
      }
    });

    String path = dir.getPath() + StringUtil.repeat("/xxx", 50) + "/fff.txt";
    File ioFile = new File(path);
    boolean b = ioFile.getParentFile().mkdirs();
    assertTrue(b);
    boolean c = ioFile.createNewFile();
    assertTrue(c);
    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(ioFile.getPath().replace(File.separatorChar, '/'));
    assertNotNull(file);

    PlatformTestUtil.startPerformanceTest("VF.getPath() performance failed", 3000, new ThrowableRunnable() {
      @Override
      public void run() {
        for (int i = 0; i < 1000000; ++i) {
          file.getPath();
        }
      }
    }).cpuBound().assertTiming();
  }
}
