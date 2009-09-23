package com.intellij.compiler.artifacts;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;

/**
 * @author nik
 */
public class ArtifactsCompilerTest extends ArtifactCompilerTestCase {

  public void testFileCopy() throws Exception {
    final Artifact a = addArtifact(
      root().file(createFile("file.txt", "foo").getPath())
    );
    compileProject();
    assertOutput(a, fs().file("file.txt", "foo").build());
  }

  public void testDir() throws Exception {
    final Artifact a = addArtifact(
      root()
        .file(createFile("abc.txt").getPath())
        .dir("dir")
          .file(createFile("xxx.txt", "bar").getPath())
    );
    compileProject();
    assertOutput(a, fs()
      .file("abc.txt")
      .dir("dir")
        .file("xxx.txt", "bar").build()
    );
  }

  public void testArchive() throws Exception {
    final Artifact a = addArtifact(
      root()
        .archive("xxx.zip")
          .file(createFile("X.class", "data").getPath())
          .dir("dir")
             .file(createFile("Y.class").getPath())
    );
    compileProject();
    assertOutput(a, fs()
      .archive("xxx.zip")
        .file("X.class", "data")
        .dir("dir")
          .file("Y.class")
          .end()
        .dir("META-INF")
          .file("MANIFEST.MF")
      .build()
    );
  }

  public void testArchiveInArchive() throws Exception {
    final Artifact a = addArtifact(
      root()
        .archive("a.jar")
          .archive("b.jar")
            .file(createFile("xxx.txt", "foo").getPath())
    );
    compileProject();
    assertOutput(a, fs()
      .archive("a.jar")
        .archive("b.jar")
          .file("xxx.txt", "foo")
          .dir("META-INF").file("MANIFEST.MF").end()
          .end()
        .dir("META-INF").file("MANIFEST.MF")
      .build());
  }

  public void testIncludedArtifact() throws Exception {
    final Artifact included = addArtifact("included", PlainArtifactType.getInstance(),
                                          root()
                                            .file(createFile("aaa.txt").getPath())
                                            .build());
    final Artifact a = addArtifact(
      root()
        .dir("dir")
          .artifact(included)
          .end()
        .file(createFile("bbb.txt").getPath())
    );
    compileProject();

    assertOutput(included, fs().file("aaa.txt").build());
    assertOutput(a, fs()
      .dir("dir")
        .file("aaa.txt")
        .end()
      .file("bbb.txt")
      .build());
  }

  public void testMergeDirectories() throws Exception {
    final Artifact included = addArtifact("included", PlainArtifactType.getInstance(),
                                          root().dir("dir").file(createFile("aaa.class").getPath()).build());
    final Artifact a = addArtifact(
      root()
        .artifact(included)
        .dir("dir")
          .file(createFile("bbb.class").getPath()));
    compileProject();
    assertOutput(a, fs()
      .dir("dir")
        .file("aaa.class")
        .file("bbb.class")
      .build());
  }

  //todo[nik] fix
  public void _testOverwriteArchives() throws Exception {
    final Artifact included = addArtifact("included", PlainArtifactType.getInstance(),
                                          root().archive("x.jar").file(createFile("aaa.class").getPath()).build());
    final Artifact a = addArtifact(
      root()
        .artifact(included)
        .archive("x.jar")
          .file(createFile("bbb.class").getPath()));
    compileProject();
    assertOutput(a, fs()
      .archive("x.jar")
        .file("aaa.class")
        .dir("META-INF").file("MANIFEST.MF")
      .build());
  }

  public void testCopyLibrary() throws Exception {
    final Library library = addProjectLibrary(null, "lib", getJDomJar());
    final Artifact a = addArtifact(root().lib(library));
    compileProject();
    assertOutput(a, fs().file("jdom.jar").build());
  }

  public void testFileOrder() throws Exception {
    final Artifact a1 = addArtifact("included1", PlainArtifactType.getInstance(),
                                    root().dir("ddd").file(createFile("d1/xxx.txt", "first").getPath()).build());
    final Artifact a2 = addArtifact("included2", PlainArtifactType.getInstance(),
                                    root().dir("ddd").file(createFile("d2/xxx.txt", "second").getPath()).build());
    final Artifact a = addArtifact(
      root()
      .artifact(a1)
      .dir("ddd")
        .file(createFile("d3/xxx.txt", "foo").getPath())
        .end()
      .artifact(a2)
    );
    compileProject();
    assertOutput(a, fs()
      .dir("ddd").file("xxx.txt", "first")
      .build());
  }
}
