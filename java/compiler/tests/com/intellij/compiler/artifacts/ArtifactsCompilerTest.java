package com.intellij.compiler.artifacts;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
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
    assertOutput(a, fs().file("file.txt", "foo"));
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
        .file("xxx.txt", "bar")
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
      );
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

    assertOutput(included, fs().file("aaa.txt"));
    assertOutput(a, fs()
      .dir("dir")
        .file("aaa.txt")
        .end()
      .file("bbb.txt")
      );
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
      );
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
      );
  }

  public void testCopyLibrary() throws Exception {
    final Library library = addProjectLibrary(null, "lib", getJDomJar());
    final Artifact a = addArtifact(root().lib(library));
    compileProject();
    assertOutput(a, fs().file("jdom.jar"));
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
      );
  }

  public void testIgnoredFile() throws Exception {
    final VirtualFile file = createFile("a/.svn/a.txt");
    createFile("a/svn/b.txt");
    final Artifact a = addArtifact(root().dirCopy(file.getParent().getParent().getPath()));
    compileProject();
    assertOutput(a, fs().dir("svn").file("b.txt"));
  }

  public void testExcludedFile() throws Exception {
    final VirtualFile file = createFile("xxx/excluded/a.txt");
    createFile("xxx/b.txt");
    final VirtualFile dir = file.getParent().getParent();

    new WriteAction() {
      protected void run(final Result result) {
        myModule = createModule("myModule");
        final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
        model.addContentEntry(dir).addExcludeFolder(file.getParent());
        model.commit();
      }
    }.execute();

    final Artifact a = addArtifact(root().dirCopy(dir.getPath()));
    compileProject();
    assertOutput(a, fs().file("b.txt"));
  }
}
