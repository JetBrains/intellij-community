package com.intellij.compiler.artifacts;

import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;

/**
 * @author nik
 */
public class PackagingElementFactoryTest extends PackagingElementsTestCase {
  public void testDirectory() throws Exception {
    final CompositePackagingElement<?> root = createRoot();

    assertSame(root, dir(root, ""));
    assertSame(root, dir(root, "/"));
    assertLayout(root, "root/\n");

    assertLayout(dir(root, "sub"), "sub/\n");
    assertLayout(root, "root/\n" +
                       " sub/\n");

    assertLayout(dir(root, "/sub/subsub"), "subsub/\n");
    assertLayout(root, "root/\n" +
                       " sub/\n" +
                       "  subsub/\n");

    dir(root, "/sub2/sub3/");
    assertLayout(root, "root/\n" +
                       " sub/\n" +
                       "  subsub/\n" +
                       " sub2/\n" +
                       "  sub3/\n");
  }

  public void testArchive() throws Exception {
    final CompositePackagingElement<?> root = createRoot();
    final CompositePackagingElement<?> archive = archive(root, "/a/out.jar");
    assertLayout(archive, "out.jar\n");
    assertLayout(root, "root/\n" +
                       " a/\n" +
                       "  out.jar\n");
  }
  
  public void testFileCopy() throws Exception {
    assertLayout(file("/", "/temp/file.txt"), "file:/temp/file.txt\n");
    assertLayout(file("/xxx/yyy/", "/temp/file.txt"), "xxx/\n" +
                                                      " yyy/\n" +
                                                      "  file:/temp/file.txt\n");
  }

  private CompositePackagingElement<?> dir(final CompositePackagingElement<?> root, final String relativePath) {
    return getFactory().getOrCreateDirectory(root, relativePath);
  }

  private CompositePackagingElement<?> archive(final CompositePackagingElement<?> root, final String relativePath) {
    return getFactory().getOrCreateArchive(root, relativePath);
  }

  private PackagingElement<?> file(String outputPath, String filePath) {
    return getFactory().createFileCopy(filePath, outputPath);
  }

  private CompositePackagingElement<?> createRoot() {
    return getFactory().createDirectory("root");
  }

}
