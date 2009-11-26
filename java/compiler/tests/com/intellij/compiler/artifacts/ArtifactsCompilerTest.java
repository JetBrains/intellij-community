package com.intellij.compiler.artifacts;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;

/**
 * @author nik
 */
public class ArtifactsCompilerTest extends ArtifactCompilerTestCase {

  public void testFileCopy() throws Exception {
    final Artifact a = addArtifact(
      root().file(createFile("file.txt", "foo"))
    );
    compileProject();
    assertOutput(a, fs().file("file.txt", "foo"));
  }

  public void testDir() throws Exception {
    final Artifact a = addArtifact(
      root()
        .file(createFile("abc.txt"))
        .dir("dir")
          .file(createFile("xxx.txt", "bar"))
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
          .file(createFile("X.class", "data"))
          .dir("dir")
             .file(createFile("Y.class"))
    );
    compileProject();
    assertOutput(a, fs()
      .archive("xxx.zip")
        .file("X.class", "data")
        .dir("dir")
          .file("Y.class")
    );
  }

  public void testArchiveInArchive() throws Exception {
    final Artifact a = addArtifact(
      root()
        .archive("a.jar")
          .archive("b.jar")
            .file(createFile("xxx.txt", "foo"))
    );
    compileProject();
    assertOutput(a, fs()
      .archive("a.jar")
        .archive("b.jar")
          .file("xxx.txt", "foo")
      );
  }

  public void testIncludedArtifact() throws Exception {
    final Artifact included = addArtifact("included",
                                          root()
                                            .file(createFile("aaa.txt")));
    final Artifact a = addArtifact(
      root()
        .dir("dir")
          .artifact(included)
          .end()
        .file(createFile("bbb.txt"))
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
    final Artifact included = addArtifact("included",
                                          root().dir("dir").file(createFile("aaa.class")));
    final Artifact a = addArtifact(
      root()
        .artifact(included)
        .dir("dir")
          .file(createFile("bbb.class")));
    compileProject();
    assertOutput(a, fs()
      .dir("dir")
        .file("aaa.class")
        .file("bbb.class")
      );
  }

  public void testOverwriteArchives() throws Exception {
    final Artifact included = addArtifact("included",
                                          root().archive("x.jar").file(createFile("aaa.class")));
    final Artifact a = addArtifact(
      root()
        .artifact(included)
        .archive("x.jar")
          .file(createFile("bbb.class")));
    compileProject();
    assertOutput(a, fs()
      .archive("x.jar")
        .file("aaa.class")
      );
  }
  
  public void testOverwriteNestedArchive() throws Exception {
    final Artifact included = addArtifact("included", root().archive("a.jar").archive("b.jar").file(createFile("c.class")));
    final Artifact a = addArtifact(
      root()
        .artifact(included)
        .archive("a.jar").archive("d.jar").file(createFile("e.class")));
    compileProject();
    assertOutput(a, fs().archive("a.jar").archive("b.jar").file("c.class"));
  }

  public void testCopyLibrary() throws Exception {
    final Library library = addProjectLibrary(null, "lib", getJDomJar());
    final Artifact a = addArtifact(root().lib(library));
    compileProject();
    assertOutput(a, fs().file("jdom.jar"));
  }

  public void testFileOrder() throws Exception {
    final Artifact a1 = addArtifact("included1",
                                    root().dir("ddd").file(createFile("d1/xxx.txt", "first")));
    final Artifact a2 = addArtifact("included2",
                                    root().dir("ddd").file(createFile("d2/xxx.txt", "second")));
    final Artifact a = addArtifact(
      root()
      .artifact(a1)
      .dir("ddd")
        .file(createFile("d3/xxx.txt", "foo"))
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
    final Artifact a = addArtifact(root().dirCopy(file.getParent().getParent()));
    compileProject();
    assertOutput(a, fs().dir("svn").file("b.txt"));
  }

  public void testCopyExcludedFile() throws Exception {
    //excluded files should be copied (e.g. compile output)
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

    final Artifact a = addArtifact(root().dirCopy(dir));
    compileProject();
    assertOutput(a, fs()
                     .dir("excluded")
                       .file("a.txt")
                       .end()
                     .file("b.txt"));
  }
}
