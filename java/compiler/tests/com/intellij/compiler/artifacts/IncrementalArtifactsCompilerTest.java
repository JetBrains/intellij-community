package com.intellij.compiler.artifacts;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;

/**
 * @author nik
 */
public class IncrementalArtifactsCompilerTest extends ArtifactCompilerTestCase {

  public void testChangeFile() throws Exception {
    final VirtualFile file = createFile("file.txt");
    addArtifact(root().dir("dir").file(file));
    compileProject();
    changeFile(file);
    compileProject().assertRecompiled("file.txt");
  }

  public void testOneFileInTwoArtifacts() throws Exception {
    final VirtualFile file = createFile("file.txt");
    final Artifact a1 = addArtifact("a1",
                                    root().dir("dir").file(file).build());

    final Artifact a2 = addArtifact("a2",
                                    root().dir("dir2").file(file).build());

    compileProject();
    compile(a1).assertUpToDate();
    compile(a2).assertUpToDate();
    compileProject().assertUpToDate();

    changeFile(file);
    compile(a1).assertRecompiled("file.txt");
    compile(a1).assertUpToDate();
    compile(a2).assertRecompiled("file.txt");
    compile(a2).assertUpToDate();
    compile(a1).assertUpToDate();
    compileProject().assertUpToDate();
  }

  public void testDeleteFile() throws Exception {
    final VirtualFile file = createFile("index.html");
    addArtifact(root().file(file));

    compileProject();
    deleteFile(file);
    compileProject().assertDeleted("out/artifacts/a/index.html");
  }

  //IDEADEV-40714
  public void testOverwriteFileInArchive() throws Exception {
    final VirtualFile file1 = createFile("a/a.txt", "a");
    final VirtualFile file2 = createFile("b/a.txt", "b");
    addArtifact(root()
                 .archive("x.jar")
                  .file(file1)
                  .file(file2));
    compileProject();
    changeFile(file1);
    compileProject().assertRecompiled("a/a.txt");
  }

  public void testRenameFile() throws Exception {
    final VirtualFile file = createFile("a/a.txt");
    final Artifact a = addArtifact(root().dirCopy(file.getParent()));
    compileProject();

    assertOutput(a, fs().file("a.txt"));
    renameFile(file, "b.txt");
    compileProject();
    assertOutput(a, fs().file("b.txt"));
  }

  //IDEADEV-25840
  public void testUpdateFileIfCaseOfLetterInNameChanged() throws Exception {
    final VirtualFile file = createFile("a/a.txt");
    final Artifact a = addArtifact(root().dirCopy(file.getParent()));
    compileProject();

    assertOutput(a, fs().file("a.txt"));
    renameFile(file, "A.txt");
    compileProject();
    assertOutput(a, fs().file("A.txt"));
  }
}