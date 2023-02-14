// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.templates.github;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TemporaryDirectory;
import com.intellij.util.io.FileTreePrinterKt;
import com.intellij.util.io.TestFileSystemBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.zip.ZipInputStream;

import static com.intellij.testFramework.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Sergey Simonchik
 */
public class ZipUtilTest {
  @Rule
  public final TemporaryDirectory tempDirRule = new TemporaryDirectory();

  @Test
  public void testSimpleUnzip() throws Exception {
    Path tempDir = tempDirRule.newPath();
    Path simpleZipFile = getZipParentDir().resolve("simple.zip");
    try (ZipInputStream stream = new ZipInputStream(Files.newInputStream(simpleZipFile))) {
      ZipUtil.unzip(null, tempDir, stream, null, null, true);
      assertThat(FileTreePrinterKt.getDirectoryTree(tempDir, Collections.emptySet(), false, false)).isEqualTo("""
                                                                                                                /
                                                                                                                  a.txt
                                                                                                                  /
                                                                                                                    b.txt
                                                                                                                """);
    }
  }

  @Test
  public void testSingleRootDirUnzip() throws Exception {
    Path tempDir = FileUtil.createTempDirectory("unzip-test-", null).toPath();
    File simpleZipFile = getZipParentDir().resolve("single-root-dir-archive.zip").toFile();
    try (ZipInputStream stream = new ZipInputStream(new FileInputStream(simpleZipFile))) {
      ZipUtil.unzip(null, tempDir, stream, null, null, true);
      checkFileStructure(tempDir,
                         TestFileSystemBuilder.fs()
                           .file("a.txt")
                           .dir("dir").file("b.txt"));
    }
  }

  @Test
  public void testSimpleUnzipUsingFile() throws Exception {
    Path tempDir = FileUtil.createTempDirectory("unzip-test-", null).toPath();
    File simpleZipFile = getZipParentDir().resolve("simple.zip").toFile();
    ZipUtil.unzip(null, tempDir.toFile(), simpleZipFile, null, null, true);
    checkFileStructure(tempDir,
                       TestFileSystemBuilder.fs()
                         .file("a.txt")
                         .dir("dir").file("b.txt"));
  }

  @Test
  public void testSingleRootDirUnzipUsingFile() throws Exception {
    Path tempDir = FileUtil.createTempDirectory("unzip-test-", null).toPath();
    File simpleZipFile = getZipParentDir().resolve("single-root-dir-archive.zip").toFile();
    ZipUtil.unzip(null, tempDir.toFile(), simpleZipFile, null, null, true);
    checkFileStructure(tempDir,
                       TestFileSystemBuilder.fs()
                         .file("a.txt")
                         .dir("dir").file("b.txt"));
  }

  @Test
  public void testExpectedFailureOnBrokenZipArchive() throws Exception {
    File tempDir = FileUtil.createTempDirectory("unzip-test-", null);
    File file = getZipParentDir().resolve("invalid-archive.zip").toFile();
    try {
      ZipUtil.unzip(null, tempDir, file, null, null, true);
      fail("Zip archive is broken, but it was unzipped without exceptions.");
    }
    catch (IOException e) {
      // expected exception
    }
  }

  private static void checkFileStructure(@NotNull Path parentDir, @NotNull TestFileSystemBuilder expected) {
    expected.build().assertDirectoryEqual(parentDir.toFile());
  }

  private static @NotNull Path getZipParentDir() {
    Path communityDir = Paths.get(PlatformTestUtil.getCommunityPath());
    return communityDir.resolve("platform/lang-impl/testData/platform/templates/github");
  }
}
