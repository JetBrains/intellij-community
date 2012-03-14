package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.jps.artifacts.Artifact;

import static com.intellij.util.io.TestFileSystemBuilder.fs;
import static org.jetbrains.jps.incremental.artifacts.LayoutElementTestUtil.root;

/**
 * @author nik
 */
public class ArtifactBuilderOverwriteTest extends ArtifactBuilderTestCase {
  public void testOverwriteArchives() {
    final String aFile = createFile("aaa.txt", "a");
    final String bFile = createFile("bbb.txt", "b");
    final Artifact a = addArtifact(
      root()
        .archive("x.jar").fileCopy(aFile).end()
        .archive("x.jar")
        .fileCopy(bFile));
    buildAll();
    assertOutput(a, fs()
      .archive("x.jar")
        .file("aaa.txt", "a")
      );
    buildAllAndAssertUpToDate();

    change(aFile, "a2");
    buildAll();
    assertCopied("aaa.txt");
    assertOutput(a, fs().archive("x.jar").file("aaa.txt", "a2"));
    buildAllAndAssertUpToDate();

    change(bFile, "b2");
    buildAllAndAssertUpToDate();

    delete(bFile);
    buildAllAndAssertUpToDate();
  }

  public void testOverwriteNestedArchive() {
    final String cFile = createFile("c.txt", "c");
    final String eFile = createFile("e.txt", "e");
    final Artifact a = addArtifact(
      root()
        .archive("a.jar").archive("b.jar").fileCopy(cFile).end().end()
        .archive("a.jar").archive("d.jar").fileCopy(eFile));
    buildAll();
    assertOutput(a, fs().archive("a.jar").archive("b.jar").file("c.txt", "c"));
    buildAllAndAssertUpToDate();

    change(eFile, "e2");
    buildAllAndAssertUpToDate();

    change(cFile, "c2");
    buildAll();
    assertCopied("c.txt");
    assertOutput(a, fs().archive("a.jar").archive("b.jar").file("c.txt", "c2"));
    buildAllAndAssertUpToDate();

    delete(eFile);
    buildAllAndAssertUpToDate();
  }

  public void testOverwriteFileByArchive() {
    final String xFile = createFile("x.txt", "1");
    final String jarFile = createFile("junit.jar", "123");
    Artifact a = addArtifact(root()
                               .archive("junit.jar").fileCopy(xFile).end()
                               .fileCopy(jarFile));
    buildAll();
    assertOutput(a, fs().archive("junit.jar").file("x.txt", "1"));
    buildAllAndAssertUpToDate();

    change(xFile, "2");
    buildAll();
    assertCopied("x.txt");
    assertOutput(a, fs().archive("junit.jar").file("x.txt", "2"));
    buildAllAndAssertUpToDate();

    change(jarFile, "321");
    buildAllAndAssertUpToDate();

    delete(jarFile);
    buildAllAndAssertUpToDate();
  }

  public void testOverwriteArchiveByFile() {
    final String xFile = createFile("x.txt", "1");
    final String jarFile = createFile("jdom.jar", "123");
    Artifact a = addArtifact(root()
                               .fileCopy(jarFile)
                               .archive("jdom.jar").fileCopy(xFile));
    buildAll();
    assertOutput(a, fs().file("jdom.jar", "123"));
    buildAllAndAssertUpToDate();

    change(xFile, "2");
    buildAllAndAssertUpToDate();

    change(jarFile, "321");
    buildAll();
    assertCopied("jdom.jar");
    assertOutput(a, fs().file("jdom.jar", "321"));
    buildAllAndAssertUpToDate();

    delete(xFile);
    buildAllAndAssertUpToDate();
  }

  public void testFileOrder() {
    final String firstFile = createFile("d1/xxx.txt", "first");
    final String secondFile = createFile("d2/xxx.txt", "second");
    final String fooFile = createFile("d3/xxx.txt", "foo");
    final Artifact a = addArtifact(
      root().dir("ddd")
         .fileCopy(firstFile)
         .fileCopy(fooFile)
         .fileCopy(secondFile).end()
    );
    buildAll();
    assertOutput(a, fs().dir("ddd").file("xxx.txt", "first"));
    buildAllAndAssertUpToDate();

    change(firstFile, "first2");
    buildAll();
    assertCopied("d1/xxx.txt");
    assertOutput(a, fs().dir("ddd").file("xxx.txt", "first2"));
    buildAllAndAssertUpToDate();

    change(secondFile);
    buildAllAndAssertUpToDate();

    change(fooFile);
    buildAllAndAssertUpToDate();

    delete(fooFile);
    buildAllAndAssertUpToDate();

    delete(secondFile);
    buildAllAndAssertUpToDate();
  }

  public void testDeleteOverwritingFiles() {
    final String firstFile = createFile("d1/xxx.txt", "1");
    final String secondFile = createFile("d2/xxx.txt", "2");
    final Artifact a = addArtifact("a",
      root().dir("ddd").fileCopy(firstFile).fileCopy(secondFile).fileCopy(createFile("y.txt"))
    );
    buildAll();
    assertOutput(a, fs().dir("ddd").file("xxx.txt", "1").file("y.txt"));

    delete(firstFile);
    buildAll();
    assertDeletedAndCopied("out/artifacts/a/ddd/xxx.txt", "d2/xxx.txt");
    assertOutput(a, fs().dir("ddd").file("xxx.txt", "2").file("y.txt"));
    buildAllAndAssertUpToDate();

    delete(secondFile);
    buildAll();
    assertDeleted("out/artifacts/a/ddd/xxx.txt");
    assertOutput(a, fs().dir("ddd").file("y.txt"));
  }
}
