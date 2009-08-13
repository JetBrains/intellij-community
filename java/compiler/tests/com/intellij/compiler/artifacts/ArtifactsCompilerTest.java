package com.intellij.compiler.artifacts;

import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;

/**
 * @author nik
 */
public class ArtifactsCompilerTest extends ArtifactCompilerTestCase {

  @Override
  protected void setUpProject() throws Exception {
    super.setUpProject();
    final String baseUrl = myProject.getBaseDir().getUrl();
    CompilerProjectExtension.getInstance(myProject).setCompilerOutputUrl(baseUrl + "/out");
  }

  public void testChangeFile() throws Exception {
    final VirtualFile file = createFile("file.txt");
    addArtifact(root().dir("dir").file(file.getPath()));
    compileProject();
    changeFile(file);
    compileProject().assertRecompiled("file.txt");
  }

  private void addArtifact(final PackagingElementBuilder rootBuilder) {
    addArtifact("a", PlainArtifactType.getInstance(), rootBuilder.build());
  }

  public void testOneFileInTwoArtifacts() throws Exception {
    final VirtualFile file = createFile("file.txt");
    final Artifact a1 = addArtifact("a1", PlainArtifactType.getInstance(),
                                    root().dir("dir").file(file.getPath()).build());

    final Artifact a2 = addArtifact("a2", PlainArtifactType.getInstance(),
                                    root().dir("dir2").file(file.getPath()).build());

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
    addArtifact(root().file(file.getPath()));

    compileProject();
    deleteFile(file);
    compileProject().assertDeleted("out/artifacts/a/index.html");
  }
}
