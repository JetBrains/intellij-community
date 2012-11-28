package org.jetbrains.jps.incremental.artifacts;

import com.intellij.util.PathUtil;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.IOException;

import static com.intellij.util.io.TestFileSystemBuilder.fs;
import static org.jetbrains.jps.incremental.artifacts.LayoutElementTestUtil.root;

/**
 * @author nik
 */
public class CleanArtifactOutputOnRebuildTest extends ArtifactBuilderTestCase {

  public void testCleanOutput() {
    JpsArtifact a = addArtifact(root().fileCopy(createFile("a.txt")));
    buildArtifacts(a);
    createFileInArtifactOutput(a, "b.txt");
    assertOutput(a, fs().file("a.txt").file("b.txt"));

    rebuildAll();
    assertOutput(a, fs().file("a.txt"));
  }

  public void testDoNotCleanOnRebuildIfOptionIsSwitchedOff() {
    JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(myProject).setClearOutputDirectoryOnRebuild(false);
    JpsArtifact a = addArtifact(root().fileCopy(createFile("a.txt")));
    buildArtifacts(a);
    createFileInArtifactOutput(a, "b.txt");
    rebuildAll();
    assertOutput(a, fs().file("a.txt").file("b.txt"));
  }

  public void testDoNotCleanIfContainsSourceFolder() {
    JpsArtifact a = addArtifact(root().fileCopy(createFile("a.txt")));
    addModule("m", a.getOutputPath() + "/src");
    buildArtifacts(a);
    createFileInArtifactOutput(a, "b.txt");
    rebuildAll();
    assertOutput(a, fs().file("a.txt").file("b.txt"));
  }

  public void testDoNotCleanIfContainsArtifactRoot() {
    JpsModule m = addModule("m");
    String resDir = PathUtil.getParentPath(createFile("res/a.txt"));
    m.getContentRootsList().addUrl(JpsPathUtil.pathToUrl(resDir));
    JpsArtifact a = addArtifact(root().dirCopy(resDir));
    a.setOutputPath(resDir);
    buildArtifacts(a);
    assertOutput(a, fs().file("a.txt"));

    createFile("res/b.txt");
    rebuildAll();
    assertOutput(a, fs().file("a.txt").file("b.txt"));
  }

  public void testCleanArtifactOutputIfItIsIncludedIntoAnotherArtifact() {
    JpsArtifact included = addArtifact("b", root().fileCopy(createFile("a.txt")));
    JpsArtifact a = addArtifact(root().artifact(included));
    buildArtifacts(a, included);
    createFileInArtifactOutput(included, "b.txt");
    assertOutput(included, fs().file("a.txt").file("b.txt"));
    rebuildAll();
    assertOutput(included, fs().file("a.txt"));
  }

  public void testCleanModuleOutputIfItIsIncludedInArtifact() {
    String file = createFile("src/A.java", "class A{}");
    JpsModule m = addModule("m", PathUtil.getParentPath(file));
    JpsArtifact a = addArtifact(root().module(m));
    buildArtifacts(a);
    createFileInModuleOutput(m, "b.txt");
    assertOutput(m, fs().file("A.class").file("b.txt"));

    rebuildAll();
    assertOutput(m, fs().file("A.class"));
  }

  private static void createFileInArtifactOutput(JpsArtifact a, final String name) {
    createFileInOutputDir(a.getOutputPath(), name);
  }

  private static void createFileInModuleOutput(JpsModule m, final String name) {
    File outputDirectory = JpsJavaExtensionService.getInstance().getOutputDirectory(m, false);
    assertNotNull(outputDirectory);
    createFileInOutputDir(outputDirectory.getAbsolutePath(), name);
  }

  private static void createFileInOutputDir(final String outputPath, final String fileName) {
    try {
      boolean created = new File(outputPath, fileName).createNewFile();
      assertTrue(created);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
