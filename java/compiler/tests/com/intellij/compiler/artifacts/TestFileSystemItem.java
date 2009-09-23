package com.intellij.compiler.artifacts;

import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.io.ZipUtil;
import junit.framework.Assert;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author nik
 */
public class TestFileSystemItem {
  private boolean myDirectory;
  private boolean myArchive;
  private String myName;
  private String myContent;
  private Map<String, TestFileSystemItem> myChildren = new HashMap<String, TestFileSystemItem>();

  public TestFileSystemItem(String name, boolean archive, boolean directory, String content) {
    myDirectory = directory;
    myArchive = archive;
    myName = name;
    myContent = content;
  }

  public TestFileSystemItem(String name, boolean archive, boolean directory) {
    this(name, archive, directory, null);
  }

  public void addChild(TestFileSystemItem item) {
    Assert.assertFalse(item.getName() + " already added", myChildren.containsKey(item.getName()));
    myChildren.put(item.getName(), item);
  }

  public boolean isDirectory() {
    return myDirectory;
  }

  public boolean isArchive() {
    return myArchive;
  }

  public String getName() {
    return myName;
  }

  public void assertDirectoryEqual(VirtualFile file) throws IOException {
    assertDirectoryEqual(file, "/");
  }

  private void assertDirectoryEqual(VirtualFile file, String relativePath) throws IOException {
    final VirtualFile[] actualChildren = file.getChildren();
    Set<String> notFound = new HashSet<String>(myChildren.keySet());
    for (VirtualFile child : actualChildren) {
      final String name = child.getName();
      final TestFileSystemItem item = myChildren.get(name);
      Assert.assertNotNull("unexpected file: " + relativePath + name, item);
      item.assertFileEqual(child, relativePath + name + "/");
      notFound.remove(name);
    }
    Assert.assertTrue("files " + notFound.toString() + " not found in " + relativePath, notFound.isEmpty());
  }

  private void assertFileEqual(VirtualFile file, String relativePath) throws IOException {
    Assert.assertEquals("in " + relativePath, myName, file.getName());
    if (myArchive) {
      final VirtualFile jarRoot;
      if (file.isInLocalFileSystem()) {
        jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(file);
      }
      else {
        final String pathInJar = file.getPath().substring(file.getPath().lastIndexOf(JarFileSystem.JAR_SEPARATOR) + JarFileSystem.JAR_SEPARATOR.length());
        final File dirForExtracted = PlatformTestCase.createTempDir("extracted_archive");
        ZipUtil.extract(JarFileSystem.getInstance().getJarFile(file), dirForExtracted, null);
        final VirtualFile extractedVirtualDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dirForExtracted);
        Assert.assertNotNull("extracted dir not found: " + dirForExtracted.getAbsolutePath(), extractedVirtualDir);
        final VirtualFile extractedFile = extractedVirtualDir.findFileByRelativePath(pathInJar);
        Assert.assertNotNull("extracted file not found: " + pathInJar, extractedFile);
        jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(extractedFile);
      }
      Assert.assertNotNull("archive file not found: " + relativePath, jarRoot);
      assertDirectoryEqual(jarRoot, relativePath);
    }
    else if (myDirectory) {
      Assert.assertTrue(relativePath + file.getName() + " is not a directory", file.isDirectory());
      assertDirectoryEqual(file, relativePath);
    }
    else if (myContent != null) {
      final String content = VfsUtil.loadText(file);
      Assert.assertEquals("content mismatch for " + relativePath, myContent, content);
    }
  }
}
