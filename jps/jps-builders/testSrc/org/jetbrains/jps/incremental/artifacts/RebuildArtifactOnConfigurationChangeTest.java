/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.incremental.artifacts;

import com.intellij.util.PathUtil;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
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

  public void testAddRootChangingRootIndices() {
    String file1 = createFile("d1/a/b/1.txt");
    String file2 = createFile("d2/x/y/2.txt");
    JpsArtifact a = addArtifact(root().fileCopy(file1).fileCopy(file2));
    buildAll();
    assertOutput(a, fs().file("1.txt").file("2.txt"));

    JpsCompositePackagingElement root = a.getRootElement();
    assertEquals(2, root.getChildren().size());
    JpsPackagingElement last = root.getChildren().get(1);
    root.removeChild(last);
    String file3 = createFile("d3/1/2/3.txt");
    root.addChild(JpsPackagingElementFactory.getInstance().createFileCopy(file3, null));
    root.addChild(last);

    buildAll();
    assertOutput(a, fs().file("1.txt").file("2.txt").file("3.txt"));
  }
}
