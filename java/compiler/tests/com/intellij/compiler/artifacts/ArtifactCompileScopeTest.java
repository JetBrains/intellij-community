package com.intellij.compiler.artifacts;

import com.intellij.compiler.CompilerTestUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;

import static com.intellij.compiler.artifacts.ArtifactsTestCase.commitModel;

/**
 * @author nik
 */
public class ArtifactCompileScopeTest extends ArtifactCompilerTestCase {
  @Override
  protected void setUpProject() throws Exception {
    super.setUpProject();
    CompilerTestUtil.setupJavacForTests(myProject);
  }

  public void testMakeModule() throws Exception {
    final VirtualFile file1 = createFile("src1/A.java", "public class A {}");
    final Module module1 = addModule("module1", file1.getParent());
    final VirtualFile file2 = createFile("src2/B.java", "public class B {}");
    final Module module2 = addModule("module2", file2.getParent());
    CompilerTestUtil.scanSourceRootsToRecompile(myProject);

    final Artifact artifact = addArtifact(root().module(module1));

    make(module1);
    assertNoOutput(artifact);

    setBuildOnMake(artifact);

    make(module2);
    assertNoOutput(artifact);

    make(module1);
    assertOutput(artifact, fs().file("A.class"));
  }

  private void setBuildOnMake(Artifact artifact) {
    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    model.getOrCreateModifiableArtifact(artifact).setBuildOnMake(true);
    commitModel(model);
  }

  public void testMakeFullArtifactIfIncludedModuleIsCompiled() throws Exception {
    final VirtualFile file1 = createFile("src1/A.java", "public class A{}");
    final Module module1 = addModule("module1", file1.getParent());
    final VirtualFile file2 = createFile("src2/B.java", "public class B {}");
    final Module module2 = addModule("module2", file2.getParent());
    CompilerTestUtil.scanSourceRootsToRecompile(myProject);

    final Artifact artifact = addArtifact(root().module(module1).module(module2));

    setBuildOnMake(artifact);

    make(module1);
    assertOutput(artifact, fs().file("A.class").file("B.class"));
  }

  //IDEA-58529
  public void testDoNotRebuildArtifactOnForceCompileOfSingleFile() throws Exception {
    final VirtualFile file1 = createFile("src/A.java", "public class A{}");
    final VirtualFile file2 = createFile("src/B.java", "public class B{}");
    final Module module = addModule("module", file1.getParent());
    CompilerTestUtil.scanSourceRootsToRecompile(myProject);

    final Artifact artifact = addArtifact(root().module(module));
    setBuildOnMake(artifact);

    make(module);
    assertOutput(artifact, fs().file("A.class").file("B.class"));

    compile(false, file1).assertUpToDate();

    ensureTimeChanged();
    final String[] aPath = {"out/production/module/A.class"};
    //file should be deleted and recompiled by javac and recompiled by artifacts compiler
    compile(true, file1).assertRecompiledAndDeleted(aPath, aPath);

    ensureTimeChanged();
    make(module).assertUpToDate();

    ensureTimeChanged();
    final String[] bothPaths = {"out/production/module/A.class", "out/production/module/B.class"};
    compile(true, file1, file2).assertRecompiledAndDeleted(bothPaths,bothPaths);
  }

  private static void ensureTimeChanged() {
    //ensure that compiler will threat the file as changed. On Linux system timestamp may be rounded to multiple of 1000
    if (SystemInfo.isLinux) {
      try {
        Thread.sleep(1100);
      }
      catch (InterruptedException ignored) {
      }
    }
  }
}
