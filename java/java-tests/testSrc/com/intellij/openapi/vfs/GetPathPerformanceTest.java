package com.intellij.openapi.vfs;

import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import java.io.File;
import java.io.IOException;

public class GetPathPerformanceTest extends IdeaTestCase {

  public void testGetPath() throws IOException {
    File dir = createTempDirectory();
    File subdir1 = new File(dir, "1");
    File subdir2 = new File(dir, "2");
    subdir1.mkdir();
    subdir2.mkdir();
    for (int i = 0; i < 10; ++i) {
      new File(subdir1, "" + i).createNewFile();
      new File(subdir2, "" + i).createNewFile();
    }
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getPath().replace(File.separatorChar, '/'));
    assertNotNull(file);

    final VirtualFile[] children = file.getChildren();
    for( VirtualFile child: children) {
      child.getPath();
    }
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        for( int i = 0; i < 1000000; ++i ) {
          for( VirtualFile child: children) {
            child.getPath();
          }
        }
      }
    };
    PlatformTestUtil.assertTiming("Performance failed", 2000, runnable);
  }
}
