package org.jetbrains.jps.util

import junit.framework.Assert

/**
 * @author nik
 */
class FileSystemItem {
  boolean directory = false
  boolean archive = false
  String name
  String content = null
  private final Map<String, FileSystemItem> children = [:]

  FileSystemItem leftShift(FileSystemItem item) {
    assert !children.containsKey(item.getName()) : "${item.name} already added"
    children[item.name] = item
    return this
  }

  def assertDirectoryEqual(File file, String relativePath) throws IOException {
    Set<String> notFound = new HashSet<String>(children.keySet());
    file.listFiles()?.each {File child ->
      final def name = child.name
      final def item = children[name]
      Assert.assertNotNull("unexpected file: $relativePath$name", item)
      item.assertFileEqual(child, relativePath + name + "/")
      notFound.remove(name)
    }
    Assert.assertTrue("files $notFound not found in $relativePath", notFound.isEmpty());
  }

  def assertFileEqual(File file, String relativePath) throws IOException {
    Assert.assertEquals("in $relativePath", name, file.name);
    if (archive) {
      final File dirForExtracted = FileUtil.createTempDirectory("extracted_archive");
      ZipUtil.extract(file, dirForExtracted, null);
      assertDirectoryEqual(dirForExtracted, relativePath);
    }
    else if (directory) {
      Assert.assertTrue("$relativePath${file.name} is not a directory", file.isDirectory());
      assertDirectoryEqual(file, relativePath);
    }
    else if (content != null) {
      final String actualContent = new String(FileUtil.loadFileText(file));
      Assert.assertEquals("content mismatch for " + relativePath, content, actualContent);
    }
  }
}
