package com.intellij.platform.templates.github;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.io.TestFileSystemBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.zip.ZipInputStream;

import static com.intellij.util.io.TestFileSystemBuilder.fs;

/**
 * @author Sergey Simonchik
 */
public class ZipUtilTest {

  @Test
  public void testSimpleUnzip() throws Exception {
    File tempDir = FileUtil.createTempDirectory("unzip-test-", null);
    File simpleZipFile = new File(getZipParentDir(), "simple.zip");
    ZipInputStream stream = new ZipInputStream(new FileInputStream(simpleZipFile));
    try {
      ZipUtil.unzip(null, tempDir, stream, null, null, true);
      checkFileStructure(tempDir,
                         fs()
                           .file("a.txt")
                           .dir("dir").file("b.txt"));
    }
    finally {
      stream.close();
    }
  }

  @Test
  public void testSingleRootDirUnzip() throws Exception {
    File tempDir = FileUtil.createTempDirectory("unzip-test-", null);
    File simpleZipFile = new File(getZipParentDir(), "single-root-dir-archive.zip");
    ZipInputStream stream = new ZipInputStream(new FileInputStream(simpleZipFile));
    try {
      ZipUtil.unzip(null, tempDir, stream, null, null, true);
      checkFileStructure(tempDir,
                         fs()
                           .file("a.txt")
                           .dir("dir").file("b.txt"));
    }
    finally {
      stream.close();
    }
  }

  private static void checkFileStructure(@NotNull File parentDir, @NotNull TestFileSystemBuilder expected) {
    expected.build().assertDirectoryEqual(parentDir);
  }

  @NotNull
  private static File getZipParentDir() {
    File communityDir = new File(PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/'));
    return new File(communityDir, "platform/lang-impl/testData/platform/templates/github");
  }

}
