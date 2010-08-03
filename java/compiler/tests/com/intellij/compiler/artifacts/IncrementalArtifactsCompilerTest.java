package com.intellij.compiler.artifacts;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;

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
                                    root().dir("dir").file(file));

    final Artifact a2 = addArtifact("a2",
                                    root().dir("dir2").file(file));

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

  //IDEADEV-41556
  public void testDeleteFilesFromSelectedArtifactsOnly() throws Exception {
    final VirtualFile file = createFile("a/a.txt");
    final Artifact a1 = addArtifact("a1", root().dirCopy(file.getParent()));
    final Artifact a2 = addArtifact("a2", root().dirCopy(file.getParent()));

    compileProject();
    assertOutput(a1, fs().file("a.txt"));
    assertOutput(a2, fs().file("a.txt"));

    deleteFile(file);
    compile(a1).assertDeleted("out/artifacts/a1/a.txt");
    assertEmptyOutput(a1);
    assertOutput(a2, fs().file("a.txt"));

    compile(a2).assertDeleted("out/artifacts/a2/a.txt");
    assertEmptyOutput(a1);
    assertEmptyOutput(a2);
  }

  //todo[nik] uncomment when deleting obsolete directories will be supported
  public void _testRenameDirectoryInLayout() throws Exception {
    final VirtualFile file = createFile("a.txt");
    final Artifact a = addArtifact("a", root().dir("d1").file(file));
    compileProject();
    assertOutput(a, fs().dir("d1").file("a.txt"));

    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    model.getOrCreateModifiableArtifact(a).setRootElement(root().dir("d2").file(file).build());
    commitModel(model);

    compileProject();
    assertOutput(a, fs().dir("d2").file("a.txt"));
  }

  public void testDeleteOutputWhenOutputPathIsChanged() throws Exception {
    final VirtualFile file = createFile("a.txt");
    final Artifact a = addArtifact("a", root().file(file));
    compileProject();
    assertOutput(a, fs().file("a.txt"));

    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    model.getOrCreateModifiableArtifact(a).setOutputPath(getProjectBasePath() + "/xxx");
    commitModel(model);

    compileProject().assertRecompiledAndDeleted(new String[]{"a.txt"}, "out/artifacts/a/a.txt");
    assertOutput(a, fs().file("a.txt"));
  }

  public void testDeleteOutputWhenArtifactIsDeleted() throws Exception {
    final VirtualFile file = createFile("a.txt");
    final Artifact a = addArtifact("a", root().file(file));
    compileProject();

    deleteArtifact(a);

    compileProject().assertDeleted("out/artifacts/a/a.txt");
    assertEmptyOutput(a);
  }

  public void testChangeFileInArchive() throws Exception {
    final VirtualFile file = createFile("a.txt", "a");
    final Artifact a = addArtifact("a", root().archive("a.jar").file(file));
    compile(a);

    final String jarPath = a.getOutputPath() + "/a.jar";
    final Artifact b = addArtifact("b", root().extractedDir(jarPath, "/"));
    compile(b);
    assertOutput(b, fs().file("a.txt", "a"));

    compile(a).assertUpToDate();
    compile(b).assertUpToDate();

    changeFile(file, "b");
    compile(b).assertUpToDate();
    compile(a).assertRecompiled("a.txt");
    assertOutput(a, fs().archive("a.jar").file("a.txt", "b"));
    changeFileInJar(jarPath, "a.txt");

    compile(b).assertRecompiled("out/artifacts/a/a.jar!/a.txt");
    assertOutput(b, fs().file("a.txt", "b"));
    compile(b).assertUpToDate();
  }
}