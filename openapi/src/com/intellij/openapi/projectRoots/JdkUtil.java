/*
 * @author max
 */
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JdkUtil {
  /**
   * @return the specified attribute of the JDK (examines rt.jar) or null if cannot determine the value
   */
  public static String getJdkMainAttribute(Sdk jdk, Attributes.Name attributeName) {
    final VirtualFile homeDirectory = jdk.getHomeDirectory();
    if (homeDirectory == null) {
      return null;
    }
    VirtualFile rtJar = homeDirectory.findFileByRelativePath("jre/lib/rt.jar");
    if (rtJar == null) {
      rtJar = homeDirectory.findFileByRelativePath("lib/rt.jar");
    }
    if (rtJar == null) {
      rtJar = homeDirectory.findFileByRelativePath("jre/lib/vm.jar"); // for IBM jdk
    }
    if (rtJar == null) {
      return null;
    }
    VirtualFile rtJarFileContent = JarFileSystem.getInstance().findFileByPath(rtJar.getPath() + JarFileSystem.JAR_SEPARATOR);
    if (rtJarFileContent == null) {
      return null;
    }
    ZipFile manifestJarFile;
    try {
      manifestJarFile = JarFileSystem.getInstance().getJarFile(rtJarFileContent);
    }
    catch (IOException e) {
      return null;
    }
    if (manifestJarFile == null) {
      return null;
    }
    try {
      ZipEntry entry = manifestJarFile.getEntry(JarFile.MANIFEST_NAME);
      if (entry == null) {
        return null;
      }
      InputStream is = manifestJarFile.getInputStream(entry);
      Manifest manifest = new Manifest(is);
      is.close();
      Attributes attributes = manifest.getMainAttributes();
      return attributes.getValue(attributeName);
    }
    catch (IOException e) {
    }
    return null;
  }
}