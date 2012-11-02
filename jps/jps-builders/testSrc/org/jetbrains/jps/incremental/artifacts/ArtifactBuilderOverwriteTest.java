package org.jetbrains.jps.incremental.artifacts;

import com.intellij.util.PathUtil;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import static com.intellij.util.io.TestFileSystemBuilder.fs;
import static org.jetbrains.jps.incremental.artifacts.LayoutElementTestUtil.root;

/**
 * @author nik
 */
public class ArtifactBuilderOverwriteTest extends ArtifactBuilderTestCase {
  public void testOverwriteArchives() {
    final String aFile = createFile("aaa.txt", "a");
    final String bFile = createFile("bbb.txt", "b");
    final JpsArtifact a = addArtifact(
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
    assertDeletedAndCopied("out/artifacts/a/x.jar", "aaa.txt");
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
    final JpsArtifact a = addArtifact(
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
    assertDeletedAndCopied("out/artifacts/a/a.jar", "c.txt");
    assertOutput(a, fs().archive("a.jar").archive("b.jar").file("c.txt", "c2"));
    buildAllAndAssertUpToDate();

    delete(eFile);
    buildAllAndAssertUpToDate();
  }

  public void testOverwriteFileByArchive() {
    final String xFile = createFile("x.txt", "1");
    final String jarFile = createFile("lib/junit.jar", "123");
    JpsArtifact a = addArtifact(root()
                               .archive("junit.jar").fileCopy(xFile).end().parentDirCopy(jarFile));
    buildAll();
    assertOutput(a, fs().archive("junit.jar").file("x.txt", "1"));
    buildAllAndAssertUpToDate();

    change(xFile, "2");
    buildAll();
    assertDeletedAndCopied("out/artifacts/a/junit.jar", "x.txt");
    assertOutput(a, fs().archive("junit.jar").file("x.txt", "2"));
    buildAllAndAssertUpToDate();

    change(jarFile, "321");
    buildAllAndAssertUpToDate();

    delete(jarFile);
    buildAllAndAssertUpToDate();
  }

  public void testOverwriteArchiveByFile() {
    final String xFile = createFile("d/x.txt", "1");
    final String jarFile = createFile("lib/jdom.jar", "123");
    JpsArtifact a = addArtifact(root().parentDirCopy(jarFile)
                               .archive("jdom.jar").parentDirCopy(xFile));
    buildAll();
    assertOutput(a, fs().file("jdom.jar", "123"));
    buildAllAndAssertUpToDate();

    change(xFile, "2");
    buildAllAndAssertUpToDate();

    change(jarFile, "321");
    buildAll();
    assertDeletedAndCopied("out/artifacts/a/jdom.jar", "lib/jdom.jar");
    assertOutput(a, fs().file("jdom.jar", "321"));
    buildAllAndAssertUpToDate();

    delete(xFile);
    buildAllAndAssertUpToDate();
  }

  public void testFileOrder() {
    final String firstFile = createFile("d1/xxx.txt", "first");
    final String secondFile = createFile("d2/xxx.txt", "second");
    final String fooFile = createFile("d3/xxx.txt", "foo");
    final JpsArtifact a = addArtifact(
      root().dir("ddd")
         .dirCopy(PathUtil.getParentPath(firstFile))
         .dirCopy(PathUtil.getParentPath(fooFile)).parentDirCopy(secondFile).end()
    );
    buildAll();
    assertOutput(a, fs().dir("ddd").file("xxx.txt", "first"));
    buildAllAndAssertUpToDate();

    change(firstFile, "first2");
    buildAll();
    assertDeletedAndCopied("out/artifacts/a/ddd/xxx.txt", "d1/xxx.txt");
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
    final JpsArtifact a = addArtifact("a",
      root().dir("ddd").dirCopy(PathUtil.getParentPath(firstFile)).parentDirCopy(secondFile).fileCopy(createFile("y.txt"))
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

  public void testUpdateManifest() {
    final String manifestText1 = "Manifest-Version: 1.0\r\nMain-Class: A\r\n\r\n";
    final String manifest = createFile("d/MANIFEST.MF", manifestText1);
    final JpsArtifact a = addArtifact("a", root().archive("a.jar").dir("META-INF").parentDirCopy(manifest).fileCopy(createFile("a.txt")));
    buildAll();
    assertOutput(a, fs().archive("a.jar").dir("META-INF").file("MANIFEST.MF", manifestText1).file("a.txt"));

    final String manifestText2 = "Manifest-Version: 1.0\r\nMain-Class: B\r\n\r\n";
    change(manifest, manifestText2);
    buildAll();
    assertDeletedAndCopied("out/artifacts/a/a.jar", "d/MANIFEST.MF", "a.txt");
    assertOutput(a, fs().archive("a.jar").dir("META-INF").file("MANIFEST.MF", manifestText2).file("a.txt"));
    buildAllAndAssertUpToDate();

    delete(manifest);
    buildAll();
    assertDeletedAndCopied("out/artifacts/a/a.jar", "a.txt");
    assertOutput(a, fs().archive("a.jar").dir("META-INF").file("a.txt"));
  }
}
