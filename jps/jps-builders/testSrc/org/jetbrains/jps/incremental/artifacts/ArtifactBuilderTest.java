/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static com.intellij.util.io.TestFileSystemBuilder.fs;
import static org.jetbrains.jps.incremental.artifacts.LayoutElementTestUtil.archive;
import static org.jetbrains.jps.incremental.artifacts.LayoutElementTestUtil.root;

/**
 * @author nik
 */
public class ArtifactBuilderTest extends ArtifactBuilderTestCase {
  public void testFileCopy() {
    final JpsArtifact a = addArtifact(root().fileCopy(createFile("file.txt", "foo")));
    buildAll();
    assertOutput(a, fs().file("file.txt", "foo"));
  }

  public void testDir() {
    final JpsArtifact a = addArtifact(
      root()
        .fileCopy(createFile("abc.txt"))
        .dir("dir")
          .fileCopy(createFile("xxx.txt", "bar"))
    );
    buildAll();
    assertOutput(a, fs()
      .file("abc.txt")
      .dir("dir")
        .file("xxx.txt", "bar")
    );
  }

  public void testArchive() {
    final JpsArtifact a = addArtifact(
      root()
        .archive("xxx.zip")
          .fileCopy(createFile("X.class", "data"))
          .dir("dir")
             .fileCopy(createFile("Y.class"))
    );
    buildAll();
    assertOutput(a, fs()
      .archive("xxx.zip")
        .file("X.class", "data")
        .dir("dir")
          .file("Y.class")
    );
  }

  public void testTwoDirsInArchive() {
    final String dir1 = PathUtil.getParentPath(PathUtil.getParentPath(createFile("dir1/a/x.txt")));
    final String dir2 = PathUtil.getParentPath(PathUtil.getParentPath(createFile("dir2/a/y.txt")));
    final JpsArtifact a = addArtifact(
      root()
        .archive("a.jar")
          .dirCopy(dir1)
          .dirCopy(dir2)
          .dir("a").fileCopy(createFile("z.txt"))
    );
    buildAll();
    assertOutput(a, fs()
      .archive("a.jar")
        .dir("a")
          .file("x.txt")
          .file("y.txt")
          .file("z.txt")
    );
  }

  public void testArchiveInArchive() {
    final JpsArtifact a = addArtifact(
      root()
        .archive("a.jar")
          .archive("b.jar")
            .fileCopy(createFile("xxx.txt", "foo"))
    );
    buildAll();
    assertOutput(a, fs()
      .archive("a.jar")
        .archive("b.jar")
          .file("xxx.txt", "foo")
      );
  }

  public void testIncludedArtifact() {
    final JpsArtifact included = addArtifact("included",
                                          root()
                                            .fileCopy(createFile("aaa.txt")));
    final JpsArtifact a = addArtifact(
      root()
        .dir("dir")
          .artifact(included)
          .end()
        .fileCopy(createFile("bbb.txt"))
    );
    buildAll();

    assertOutput(included, fs().file("aaa.txt"));
    assertOutput(a, fs()
      .dir("dir")
        .file("aaa.txt")
        .end()
      .file("bbb.txt")
      );
  }

  public void testMergeDirectories() {
    final JpsArtifact included = addArtifact("included",
                                          root().dir("dir").fileCopy(createFile("aaa.class")));
    final JpsArtifact a = addArtifact(
      root()
        .artifact(included)
        .dir("dir")
          .fileCopy(createFile("bbb.class")));
    buildAll();
    assertOutput(a, fs()
      .dir("dir")
        .file("aaa.class")
        .file("bbb.class")
      );
  }

  public void testCopyLibrary() {
    final JpsLibrary library = addProjectLibrary("lib", getJUnitJarPath());
    final JpsArtifact a = addArtifact(root().lib(library));
    buildAll();
    assertOutput(a, fs().file("junit.jar"));
  }

  public void testModuleOutput() {
    final String file = createFile("src/A.java", "public class A {}");
    final JpsModule module = addModule("a", PathUtil.getParentPath(file));
    final JpsArtifact artifact = addArtifact(root().module(module));

    buildArtifacts(artifact);
    assertOutput(artifact, fs().file("A.class"));
  }

  public void testCopyResourcesFromModuleOutput() {
    String file = createFile("src/a.xml", "");
    JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(myProject).addResourcePattern("*.xml");
    JpsModule module = addModule("a", PathUtil.getParentPath(file));
    JpsArtifact artifact = addArtifact(root().module(module));
    buildArtifacts(artifact);
    assertOutput(artifact, fs().file("a.xml"));
  }

  public void testIgnoredFile() {
    final String file = createFile("a/.svn/a.txt");
    createFile("a/svn/b.txt");
    final JpsArtifact a = addArtifact(root().parentDirCopy(PathUtil.getParentPath(file)));
    buildAll();
    assertOutput(a, fs().dir("svn").file("b.txt"));
  }

  public void testIgnoredFileInArchive() {
    final String file = createFile("a/.svn/a.txt");
    createFile("a/svn/b.txt");
    final JpsArtifact a = addArtifact(archive("a.jar").parentDirCopy(PathUtil.getParentPath(file)));
    buildAll();
    assertOutput(a, fs().archive("a.jar").dir("svn").file("b.txt"));
  }

  public void testCopyExcludedFolder() {
    //explicitly added excluded files should be copied (e.g. compile output)
    final String file = createFile("xxx/excluded/a.txt");
    createFile("xxx/excluded/CVS");
    final String excluded = PathUtil.getParentPath(file);
    final String dir = PathUtil.getParentPath(excluded);

    final JpsModule module = addModule("myModule");
    module.getContentRootsList().addUrl(JpsPathUtil.pathToUrl(dir));
    module.getExcludeRootsList().addUrl(JpsPathUtil.pathToUrl(excluded));

    final JpsArtifact a = addArtifact(root().dirCopy(excluded));
    buildAll();
    assertOutput(a, fs().file("a.txt"));
  }

  public void testCopyExcludedFile() {
    //excluded files under non-excluded directory should not be copied
    final String file = createFile("xxx/excluded/a.txt");
    createFile("xxx/b.txt");
    createFile("xxx/CVS");
    final String dir = PathUtil.getParentPath(PathUtil.getParentPath(file));

    JpsModule module = addModule("myModule");
    module.getContentRootsList().addUrl(JpsPathUtil.pathToUrl(dir));
    module.getExcludeRootsList().addUrl(JpsPathUtil.pathToUrl(PathUtil.getParentPath(file)));

    final JpsArtifact a = addArtifact(root().dirCopy(dir));
    buildAll();
    assertOutput(a, fs().file("b.txt"));
  }

  public void testExtractDirectory() {
    final JpsArtifact a = addArtifact("a", root().dir("dir").extractedDir(getJUnitJarPath(), "/junit/textui/"));
    buildAll();
    assertOutput(a, fs().dir("dir")
                           .file("ResultPrinter.class")
                           .file("TestRunner.class"));
  }

  public void testExtractDirectoryFromExcludedJar() throws IOException {
    String jarPath = createFile("dir/lib/j.jar");
    FileUtil.copy(new File(getJUnitJarPath()), new File(jarPath));
    JpsModule module = addModule("m");
    String libDir = PathUtil.getParentPath(jarPath);
    module.getContentRootsList().addUrl(JpsPathUtil.pathToUrl(PathUtil.getParentPath(libDir)));
    module.getExcludeRootsList().addUrl(JpsPathUtil.pathToUrl(libDir));
    final JpsArtifact a = addArtifact("a", root().extractedDir(jarPath, "/junit/textui/"));
    buildAll();
    assertOutput(a, fs().file("ResultPrinter.class")
                        .file("TestRunner.class"));
  }

  public void testPackExtractedDirectory() {
    final JpsArtifact a = addArtifact("a", root().archive("a.jar").extractedDir(getJUnitJarPath(), "/junit/textui/"));
    buildAll();
    assertOutput(a, fs().archive("a.jar")
                           .file("ResultPrinter.class")
                           .file("TestRunner.class"));
  }

  public void testSelfIncludingArtifact() {
    final JpsArtifact a = addArtifact("a", root());
    LayoutElementTestUtil.addArtifactToLayout(a, a);
    assertBuildFailed(a);
  }

  public void testCircularInclusion() {
    final JpsArtifact a = addArtifact("a", root());
    final JpsArtifact b = addArtifact("b", root());
    LayoutElementTestUtil.addArtifactToLayout(a, b);
    LayoutElementTestUtil.addArtifactToLayout(b, a);
    assertBuildFailed(a);
    assertBuildFailed(b);
  }

  public void testArtifactContainingSelfIncludingArtifact() {
    JpsArtifact c = addArtifact("c", root());
    final JpsArtifact a = addArtifact("a", root().artifact(c));
    LayoutElementTestUtil.addArtifactToLayout(a, a);
    final JpsArtifact b = addArtifact("b", root().artifact(a));

    buildArtifacts(c);
    assertBuildFailed(b);
    assertBuildFailed(a);
  }

  public void testArtifactContainingSelfIncludingArtifactWithoutOutput() {
    final JpsArtifact a = addArtifact("a", root());
    LayoutElementTestUtil.addArtifactToLayout(a, a);
    final JpsArtifact b = addArtifact("b", root().artifact(a));
    a.setOutputPath(null);

    assertBuildFailed(b);
  }

  //IDEA-73893
  public void testManifestFileIsFirstEntry() throws IOException {
    final String firstFile = createFile("src/A.txt");
    final String manifestFile = createFile("src/MANIFEST.MF");
    final String lastFile = createFile("src/Z.txt");
    final JpsArtifact a = addArtifact(archive("a.jar").dir("META-INF")
                                       .fileCopy(firstFile).fileCopy(manifestFile).fileCopy(lastFile));
    buildArtifacts(a);
    final String jarPath = a.getOutputPath() + "/a.jar";
    JarFile jarFile = new JarFile(new File(jarPath));
    try {
      final Enumeration<JarEntry> entries = jarFile.entries();
      assertTrue(entries.hasMoreElements());
      final JarEntry firstEntry = entries.nextElement();
      assertEquals(JarFile.MANIFEST_NAME, firstEntry.getName());
    }
    finally {
      jarFile.close();
    }
  }

  public void testPreserveCompressionMethodForEntryExtractedFromOneArchiveAndPackedIntoAnother() throws IOException {
    String path = createFile("data/a.jar");
    ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(new File(path))));
    try {
      ZipEntry entry = new ZipEntry("a.txt");
      byte[] text = "text".getBytes();
      entry.setMethod(ZipEntry.STORED);
      entry.setSize(text.length);
      CRC32 crc32 = new CRC32();
      crc32.update(text);
      entry.setCrc(crc32.getValue());
      output.putNextEntry(entry);
      output.write(text);
      output.closeEntry();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    finally {
      output.close();
    }
    JpsArtifact a = addArtifact(archive("b.jar").extractedDir(path, ""));
    buildAll();
    assertOutput(a, fs().archive("b.jar").file("a.txt", "text"));

    final String jarPath = a.getOutputPath() + "/b.jar";
    ZipFile zipFile = new ZipFile(new File(jarPath));
    try {
      ZipEntry entry = zipFile.getEntry("a.txt");
      assertNotNull(entry);
      assertEquals(ZipEntry.STORED, entry.getMethod());
    }
    finally {
      zipFile.close();
    }
  }
  
  public void testBuildModuleBeforeArtifactIfSomeDirectoryInsideModuleOutputIsCopiedToArtifact() {
    String src = PathUtil.getParentPath(PathUtil.getParentPath(createFile("src/x/A.java", "package x; class A{}")));
    JpsModule module = addModule("m", src);
    File output = JpsJavaExtensionService.getInstance().getOutputDirectory(module, false);
    JpsArtifact artifact = addArtifact(root().dirCopy(new File(output, "x").getAbsolutePath()));
    rebuildAllModulesAndArtifacts();
    assertOutput(module, fs().dir("x").file("A.class"));
    assertOutput(artifact, fs().file("A.class"));
  }
  
  public void testClearOutputOnRebuild() {
    String file = createFile("d/a.txt");
    JpsArtifact a = addArtifact(root().parentDirCopy(file));
    buildAll();
    createFileInArtifactOutput(a, "b.txt");
    buildAllAndAssertUpToDate();
    assertOutput(a, fs().file("a.txt").file("b.txt"));

    rebuildAllModulesAndArtifacts();
    assertOutput(a, fs().file("a.txt").file("b.txt"));
  }

  public void testDeleteOnlyOutputFileOnRebuildForArchiveArtifact() {
    String file = createFile("a.txt");
    JpsArtifact a = addArtifact(archive("a.jar").fileCopy(file));
    buildAll();
    createFileInArtifactOutput(a, "b.txt");
    buildAllAndAssertUpToDate();
    assertOutput(a, fs().archive("a.jar").file("a.txt").end().file("b.txt"));

    rebuildAllModulesAndArtifacts();
    assertOutput(a, fs().archive("a.jar").file("a.txt").end().file("b.txt"));
  }

  public void testDoNotCreateEmptyArchive() {
    String file = createFile("dir/a.txt");
    JpsArtifact a = addArtifact(archive("a.jar").parentDirCopy(file));
    delete(file);
    buildAll();
    assertEmptyOutput(a);
  }

  public void testDoNotCreateEmptyArchiveInsideArchive() {
    String file = createFile("dir/a.txt");
    JpsArtifact a = addArtifact(archive("a.jar").archive("inner.jar").parentDirCopy(file));
    delete(file);
    buildAll();
    assertEmptyOutput(a);
  }

  public void testDoNotCreateEmptyArchiveFromExtractedDirectory() {
    final JpsArtifact a = addArtifact("a", archive("a.jar").dir("dir").extractedDir(getJUnitJarPath(), "/xxx/"));
    buildAll();
    assertEmptyOutput(a);
  }

  public void testExtractNonExistentJarFile() {
    JpsArtifact a = addArtifact(root().extractedDir("this-file-does-not-exist.jar", "/"));
    buildAll();
    assertEmptyOutput(a);
  }

  public void testRepackNonExistentJarFile() {
    JpsArtifact a = addArtifact(archive("a.jar").extractedDir("this-file-does-not-exist.jar", "/").fileCopy(createFile("a.txt")));
    buildAll();
    assertOutput(a, fs().archive("a.jar").file("a.txt"));
  }
}
