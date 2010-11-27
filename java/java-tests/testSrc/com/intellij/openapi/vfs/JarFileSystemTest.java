package com.intellij.openapi.vfs;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.IdeaTestCase;

import java.io.File;

public class JarFileSystemTest extends IdeaTestCase{
  public void testFindFile() throws Exception{
    Sdk jdk = ModuleRootManager.getInstance(myModule).getSdk();
    VirtualFile jdkHome = jdk.getHomeDirectory();
    String rtJarPath = new File(jdkHome.getPath() + "/src.zip").getCanonicalPath().replace(File.separator, "/");

    VirtualFile file1 = findByPath(rtJarPath + JarFileSystem.JAR_SEPARATOR);
    assertTrue(file1.isDirectory());

    VirtualFile file2 = findByPath(rtJarPath + JarFileSystem.JAR_SEPARATOR + "java");
    assertTrue(file2.isDirectory());

    VirtualFile file3 = file1.findChild("java");
    assertEquals(file2, file3);

    VirtualFile file4 = findByPath(rtJarPath + JarFileSystem.JAR_SEPARATOR + "java/lang/Object.java");
    assertTrue(!file4.isDirectory());

    byte[] bytes = file4.contentsToByteArray();
    assertNotNull(bytes);
    assertTrue(bytes.length > 10);
  }

  public void testMetaInf() throws Exception{
    Sdk jdk = ModuleRootManager.getInstance(myModule).getSdk();
    VirtualFile jdkHome = jdk.getHomeDirectory();
    String rtJarPath = jdkHome.getPath() + "/jre/lib/rt.jar";

    VirtualFile jarRoot = JarFileSystem.getInstance().findFileByPath(rtJarPath + JarFileSystem.JAR_SEPARATOR);
    assertNotNull(jarRoot);

    VirtualFile metaInf = jarRoot.findChild("META-INF");
    assertNotNull(metaInf);

    VirtualFile[] children = metaInf.getChildren();
    assertEquals(1, children.length);
  }

  private static VirtualFile findByPath(String path) {
    VirtualFile file = JarFileSystem.getInstance().findFileByPath(path);
    assertNotNull(file);
    final String filePath = file.getPath();
    final String message = "paths are not equal, path1 = " + path + " found: " + filePath;
    if (SystemInfo.isFileSystemCaseSensitive) {
      assertEquals(message, path, file.getPath());
    }
    else {
      assertTrue(message, path.equalsIgnoreCase(filePath));
    }
    return file;
  }
}
