package com.intellij.platform.templates.github;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipInputStream;

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
      checkFileStructure(tempDir, new String[] {"a.txt", "dir/b.txt"});
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
      checkFileStructure(tempDir, new String[] {"a.txt", "dir/b.txt"});
    }
    finally {
      stream.close();
    }
  }

  private static void checkFileStructure(@NotNull File parentDir, @NotNull String[] expectedPaths) {
    List<String> paths = ContainerUtil.newArrayList();
    collectPaths(parentDir, paths, "");
    String[] actualPaths = ArrayUtil.toStringArray(paths);

    Arrays.sort(expectedPaths);
    Arrays.sort(actualPaths);
    Assert.assertArrayEquals(expectedPaths, actualPaths);
  }

  private static void collectPaths(@NotNull File dir,
                                   @NotNull List<String> paths,
                                   @NotNull String prefix) {
    if (dir.isFile()) {
      paths.add(prefix);
      return;
    }
    File[] files = dir.listFiles();
    if (files == null) {
      return;
    }
    if (!prefix.isEmpty()) {
      prefix += '/';
    }
    for (File file : files) {
      collectPaths(file, paths, prefix + file.getName());
    }
  }

  @NotNull
  private static File getZipParentDir() {
    File communityDir = new File(PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/'));
    return new File(communityDir, "platform/lang-impl/testData/platform/templates/github");
  }

}
