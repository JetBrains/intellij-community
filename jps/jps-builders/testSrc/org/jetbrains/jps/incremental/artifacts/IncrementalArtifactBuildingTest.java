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
import org.jetbrains.jps.artifacts.Artifact;

import static com.intellij.util.io.TestFileSystemItem.fs;
import static org.jetbrains.jps.incremental.artifacts.LayoutElementTestUtil.archive;
import static org.jetbrains.jps.incremental.artifacts.LayoutElementTestUtil.root;

/**
 * @author nik
 */
public class IncrementalArtifactBuildingTest extends ArtifactBuilderTestCase {
  public void testCopyChangedFile() {
    String file1 = createFile("a.txt", "aaa");
    String file2 = createFile("b.txt", "bbb");
    final Artifact a = addArtifact(root().fileCopy(file1).fileCopy(file2));
    buildAll();
    assertOutput(a, fs().file("a.txt", "aaa").file("b.txt", "bbb"));

    buildAllAndAssertUpToDate();

    change(file1, "xxx");
    buildAll();
    assertCopied("a.txt");
    assertOutput(a, fs().file("a.txt", "xxx").file("b.txt", "bbb"));
    buildAllAndAssertUpToDate();
  }

  public void testRemoveDeletedFile() {
    String file1 = createFile("a.txt");
    String file2 = createFile("b.txt");
    final Artifact a = addArtifact("a", root().fileCopy(file1).fileCopy(file2));
    buildAll();
    assertOutput(a, fs().file("a.txt").file("b.txt"));

    delete(file1);
    buildAll();
    assertDeleted("out/artifacts/a/a.txt");
    assertOutput(a, fs().file("b.txt"));
    buildAllAndAssertUpToDate();
  }

  public void testPackChangedFile() {
    String file1 = createFile("a.txt", "aaa");
    String file2 = createFile("b.txt", "bbb");
    final Artifact a = addArtifact(archive("a.jar").fileCopy(file1).fileCopy(file2));
    buildAll();
    assertOutput(a, fs().archive("a.jar").file("a.txt", "aaa").file("b.txt", "bbb"));
    buildAllAndAssertUpToDate();

    change(file1, "xxx");
    buildAll();
    assertCopied("a.txt", "b.txt");
    assertOutput(a, fs().archive("a.jar").file("a.txt", "xxx").file("b.txt", "bbb"));
    buildAllAndAssertUpToDate();
  }

  public void testRemoveDeletedFileFromArchive() {
    String file1 = createFile("a.txt");
    String file2 = createFile("b.txt");
    final Artifact a = addArtifact("a", archive("a.jar").fileCopy(file1).fileCopy(file2));
    buildAll();
    assertOutput(a, fs().archive("a.jar").file("a.txt").file("b.txt"));

    delete(file1);
    buildAll();
    assertDeletedAndCopied("out/artifacts/a/a.jar", "b.txt");
    assertOutput(a, fs().archive("a.jar").file("b.txt"));
    buildAllAndAssertUpToDate();
  }

  public void testOneFileInTwoArtifacts() {
    final String file = createFile("file.txt");
    final Artifact a1 = addArtifact("a1", root().dir("dir").fileCopy(file));
    final Artifact a2 = addArtifact("a2", root().dir("dir2").fileCopy(file));

    buildAll();
    buildArtifacts(a1); assertUpToDate();
    buildArtifacts(a2); assertUpToDate();
    buildAllAndAssertUpToDate();

    change(file);
    buildArtifacts(a1); assertCopied("file.txt");
    buildArtifacts(a1); assertUpToDate();
    buildArtifacts(a2); assertCopied("file.txt");
    buildArtifacts(a2); assertUpToDate();
    buildArtifacts(a1); assertUpToDate();
    buildAllAndAssertUpToDate();
  }

  //IDEADEV-40714
  public void testOverwriteFileInArchive() {
    final String file1 = createFile("a/a.txt", "a");
    final String file2 = createFile("b/a.txt", "b");
    addArtifact(root()
                 .archive("x.jar")
                  .fileCopy(file1)
                  .fileCopy(file2));
    buildAll();
    change(file1);
    buildAll();
    assertCopied("a/a.txt");
  }

  public void testRenameFile() throws Exception {
    final String file = createFile("a/a.txt");
    final Artifact a = addArtifact(root().dirCopy(PathUtil.getParentPath(file)));
    buildAll();

    assertOutput(a, fs().file("a.txt"));
    rename(file, "b.txt");
    buildAll();
    assertOutput(a, fs().file("b.txt"));
  }

  //IDEADEV-25840
  public void testUpdateFileIfCaseOfLetterInNameChanged() throws Exception {
    final String file = createFile("a/a.txt");
    final Artifact a = addArtifact("a", root().dirCopy(PathUtil.getParentPath(file)));
    buildAll();

    assertOutput(a, fs().file("a.txt"));
    rename(file, "A.txt");
    buildAll();
    assertOutput(a, fs().file("A.txt"));
  }

  //IDEADEV-41556
  public void testDeleteFilesFromSelectedArtifactsOnly() throws Exception {
    final String file = createFile("a/a.txt");
    final Artifact a1 = addArtifact("a1", root().dirCopy(PathUtil.getParentPath(file)));
    final Artifact a2 = addArtifact("a2", root().dirCopy(PathUtil.getParentPath(file)));

    buildAll();
    assertOutput(a1, fs().file("a.txt"));
    assertOutput(a2, fs().file("a.txt"));

    delete(file);
    buildArtifacts(a1); assertDeleted("out/artifacts/a1/a.txt");
    assertEmptyOutput(a1);
    assertOutput(a2, fs().file("a.txt"));

    buildArtifacts(a2); assertDeleted("out/artifacts/a2/a.txt");
    assertEmptyOutput(a1);
    assertEmptyOutput(a2);
  }

  //IDEA-51910
  public void testTwoArtifactsWithSameOutput() throws Exception {
    final String res1 = PathUtil.getParentPath(createFile("res1/a.txt", "1"));
    final String res2 = PathUtil.getParentPath(createFile("res2/a.txt", "2"));
    final Artifact a1 = addArtifact("a1", root().dirCopy(res1));
    final Artifact a2 = addArtifact("a2", root().dirCopy(res2));
    a2.setOutputPath(a1.getOutputPath());
    assertEquals(a1.getOutputPath(), a2.getOutputPath());

    buildArtifacts(a1);
    assertOutput(a1, fs().file("a.txt", "1"));
    assertOutput(a2, fs().file("a.txt", "1"));
    buildArtifacts(a1); assertUpToDate();

    buildArtifacts(a2);
    assertOutput(a2, fs().file("a.txt", "2"));
  }

}
