package org.jetbrains.jps.incremental.artifacts;

import com.intellij.util.PathUtil;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElementFactory;

import static com.intellij.util.io.TestFileSystemBuilder.fs;
import static org.jetbrains.jps.incremental.artifacts.LayoutElementTestUtil.root;

/**
 * @author nik
 */
public class RebuildArtifactOnConfigurationChangeTest extends ArtifactBuilderTestCase {
  public void testAddRoot() {
    String dir1 = PathUtil.getParentPath(createFile("dir1/a.txt", "a"));
    String dir2 = PathUtil.getParentPath(createFile("dir2/b.txt", "b"));
    JpsArtifact a = addArtifact(root().dirCopy(dir1));
    buildAll();
    assertOutput(a, fs().file("a.txt", "a"));

    a.getRootElement().addChild(JpsPackagingElementFactory.getInstance().createDirectoryCopy(dir2));
    buildAll();
    assertOutput(a, fs().file("a.txt", "a").file("b.txt", "b"));
    assertDeletedAndCopied("out/artifacts/a/a.txt", "dir1/a.txt", "dir2/b.txt");
    buildAllAndAssertUpToDate();
  }

  public void testRemoveRoot() {
    String file1 = createFile("dir1/a.txt", "a");
    String file2 = createFile("dir2/b.txt", "b");
    JpsArtifact a = addArtifact(root().parentDirCopy(file1).parentDirCopy(file2));
    buildAll();
    assertOutput(a, fs().file("a.txt", "a").file("b.txt", "b"));

    a.getRootElement().removeChild(a.getRootElement().getChildren().get(0));
    buildAll();
    assertOutput(a, fs().file("b.txt", "b"));
    buildAllAndAssertUpToDate();
  }

  public void testChangeOutput() {
    String file = createFile("dir/a.txt");
    JpsArtifact a = addArtifact(root().parentDirCopy(file));
    buildAll();
    String oldOutput = a.getOutputPath();
    assertNotNull(oldOutput);
    assertOutput(oldOutput, fs().file("a.txt"));

    String newOutput = PathUtil.getParentPath(oldOutput) + "/a2";
    a.setOutputPath(newOutput);
    buildAll();
    assertOutput(newOutput, fs().file("a.txt"));
    assertOutput(oldOutput, fs());
    buildAllAndAssertUpToDate();
  }

  public void testChangeConfiguration() {
    String file = createFile("d/a.txt", "a");
    JpsArtifact a = addArtifact(root().parentDirCopy(file));
    buildAll();
    assertOutput(a, fs().file("a.txt", "a"));

    a.setRootElement(root().dir("dir").parentDirCopy(file).buildElement());
    buildAll();
    assertOutput(a, fs().dir("dir").file("a.txt", "a"));
    buildAllAndAssertUpToDate();
  }
}
