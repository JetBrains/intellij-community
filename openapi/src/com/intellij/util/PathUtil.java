/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PathUtil {

  public static String getLocalPath(VirtualFile file) {
    if (file == null || !file.isValid()) {
      return null;
    }
    if (file.getFileSystem() instanceof JarFileSystem && file.getParent() != null) {
      return null;
    }
    String path = file.getPath();
    if (path.endsWith(JarFileSystem.JAR_SEPARATOR)) {
      path = path.substring(0, path.length() - JarFileSystem.JAR_SEPARATOR.length());
    }
    return path.replace('/', File.separatorChar);
  }

  /**
   * @return the specified attribute of the JDK (examines rt.jar) or null if cannot determine the value
   */
  public static String getJdkMainAttribute(ProjectJdk jdk, Attributes.Name attributeName) {
    final VirtualFile homeDirectory = jdk.getHomeDirectory();
    if (homeDirectory == null) {
      return null;
    }
    VirtualFile rtJar = homeDirectory.findFileByRelativePath("jre/lib/rt.jar");
    if (rtJar == null) {
      rtJar = homeDirectory.findFileByRelativePath("lib/rt.jar");
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
      ZipEntry entry = manifestJarFile.getEntry("META-INF/MANIFEST.MF");
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

  public static String getJarPathForClass(final Class aClass) {
    String resourceRoot = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    return new File(resourceRoot).getAbsoluteFile().getAbsolutePath();
  }

  public static String toPresentableUrl(String url) {
    String path = VirtualFileManager.extractPath(url);
    if (path.endsWith(JarFileSystem.JAR_SEPARATOR)) {
      path = path.substring(0, path.length() - JarFileSystem.JAR_SEPARATOR.length());
    }
    return path.replace('/', File.separatorChar);
  }

  public static final String getCanonicalPath(String path){
    if (path == null || path.length() == 0) {
      return path;
    }
    path = path.replace(File.separatorChar, '/');
    final StringTokenizer tok = new StringTokenizer(path, "/");
    final Stack<String> stack = new Stack<String>();
    while(tok.hasMoreTokens()){
      final String token = tok.nextToken();

      if(token.length() == 0) {
        continue;
      }
      else if("..".equals(token)){
        if(stack.isEmpty()) {
          return null;
        }
        stack.pop();
      }
      else if (!".".equals(token)) {
        stack.push(token);
      }
    }
    final String[] strings = stack.toArray(new String[stack.size()]);
    final StringBuffer result = new StringBuffer(path.length());
    if (path.charAt(0) == '/') {
      result.append("/");
    }
    for (int i = 0; i < strings.length; i++) {
      if (i > 0) {
        result.append('/');
      }
      result.append(strings[i]);
    }
    return result.toString();
  }

}