package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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

  public void testIgnoredFile() {
    final String file = createFile("a/.svn/a.txt");
    createFile("a/svn/b.txt");
    final JpsArtifact a = addArtifact(root().dirCopy(PathUtil.getParentPath(PathUtil.getParentPath(file))));
    buildAll();
    assertOutput(a, fs().dir("svn").file("b.txt"));
  }

  public void testIgnoredFileInArchive() {
    final String file = createFile("a/.svn/a.txt");
    createFile("a/svn/b.txt");
    final JpsArtifact a = addArtifact(archive("a.jar").dirCopy(PathUtil.getParentPath(PathUtil.getParentPath(file))));
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

  //IDEA-73893
  public void testManifestFileIsFirstEntry() throws IOException {
    final String firstFile = createFile("src/A.txt");
    final String manifestFile = createFile("src/MANIFEST.MF");
    final String lastFile = createFile("src/Z.txt");
    final JpsArtifact a = addArtifact(archive("a.jar").dir("META-INF")
                                       .fileCopy(firstFile).fileCopy(manifestFile).fileCopy(lastFile));
    buildArtifacts(a);
    final String jarPath = a.getOutputPath() + "/a.jar";
    assertNotNull(jarPath);
    JarFile jarFile = new JarFile(new File(FileUtil.toSystemDependentName(jarPath)));
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

}
