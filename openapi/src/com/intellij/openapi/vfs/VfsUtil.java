/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs;

import com.intellij.j2ee.ExternalResourceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class VfsUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.VfsUtil");


  /**
   * Checks whether the <code>ancestor {@link VirtualFile}</code> is parent of <code>file
   * {@link VirtualFile}</code>.
   *
   * @param ancestor the file
   * @param file     the file
   * @param strict   if <code>false</code> then this method returns <code>true</code> if <code>ancestor</code>
   *                 and <code>file</code> are equal
   * @return <code>true</code> if <code>ancestor</code> is parent of <code>file</code>; <code>false</code> otherwise
   */
  public static boolean isAncestor(VirtualFile ancestor, VirtualFile file, boolean strict) {
    if (!file.getFileSystem().equals(ancestor.getFileSystem())) return false;
    VirtualFile parent = strict ? file.getParent() : file;
    while (true) {
      if (parent == null) return false;
      if (parent.equals(ancestor)) return true;
      parent = parent.getParent();
    }
  }

  /**
   * Gets the relative path of <code>file</code> to its <code>ancestor</code>. Uses <code>separator</code> for
   * separating files.
   *
   * @param file      the file
   * @param ancestor  parent file
   * @param separator character to use as files separator
   * @return the relative path
   */
  public static String getRelativePath(VirtualFile file, VirtualFile ancestor, char separator) {
    if (!file.getFileSystem().equals(ancestor.getFileSystem())) return null;

    int length = 0;
    VirtualFile parent = file;
    while (true) {
      if (parent == null) return null;
      if (parent.equals(ancestor)) break;
      if (length > 0) {
        length++;
      }
      length += parent.getName().length();
      parent = parent.getParent();
    }

    char[] chars = new char[length];
    int index = chars.length;
    parent = file;
    while (true) {
      if (parent.equals(ancestor)) break;
      if (index < length) {
        chars[--index] = separator;
      }
      String name = parent.getName();
      for (int i = name.length() - 1; i >= 0; i--) {
        chars[--index] = name.charAt(i);
      }
      parent = parent.getParent();
    }
    return new String(chars);
  }

  /**
   * Copies all files matching the <code>filter</code> from <code>fromDir</code> to <code>toDir</code>.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @param fromDir   the directory to copy from
   * @param toDir     the directory to copy to
   * @param filter    {@link VirtualFileFilter}
   * @throws IOException if files failed to be copied
   */
  public static void copyDirectory(Object requestor, VirtualFile fromDir, VirtualFile toDir, VirtualFileFilter filter)
    throws IOException {
    VirtualFile[] children = fromDir.getChildren();
    for (int i = 0; i < children.length; i++) {
      VirtualFile child = children[i];
      if (filter == null || filter.accept(child)) {
        if (!child.isDirectory()) {
          copyFile(requestor, child, toDir);
        }
        else {
          VirtualFile newChild = toDir.createChildDirectory(requestor, child.getName());
          copyDirectory(requestor, child, newChild, filter);
        }
      }
    }
  }

  /**
   * Makes a copy of the <code>file</code> in the <code>toDir</code> folder and returns it.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @param file      file to make a copy of
   * @param toDir     directory to make a copy in
   * @return a copy of the file
   * @throws IOException if file failed to be copied
   */
  public static VirtualFile copyFile(Object requestor, VirtualFile file, VirtualFile toDir) throws IOException {
    return copyFile(requestor, file, toDir, file.getName());
  }

  /**
   * Makes a copy of the <code>file</code> in the <code>toDir</code> folder with the <code>newName</code> and returns it.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @param file      file to make a copy of
   * @param toDir     directory to make a copy in
   * @param newName   new name of the file
   * @return a copy of the file
   * @throws IOException if file failed to be copied
   */
  public static VirtualFile copyFile(Object requestor, VirtualFile file, VirtualFile toDir, String newName)
    throws IOException {
    VirtualFile newChild = toDir.createChildData(requestor, newName);
    // [jeka] TODO: to be duscussed if the copy should have the same timestamp as the original
    //OutputStream out = newChild.getOutputStream(requestor, -1, file.getActualTimeStamp());
    OutputStream out = newChild.getOutputStream(requestor);
    out.write(file.contentsToByteArray());
    out.close();
    return newChild;
  }

  /**
   * Gets the array of common ancestors for passed files.
   *
   * @param files array of files
   * @return array of common ancestors for passed files
   */
  public static VirtualFile[] getCommonAncestors(VirtualFile[] files) {
    // Separate files by first component in the path.
    HashMap map = new HashMap();
    for (int i = 0; i < files.length; i++) {
      VirtualFile file = files[i].isDirectory() ? files[i] : files[i].getParent();
      //assertTrue(file != null);
      VirtualFile[] path = getPathComponents(file);
      Set filesSet;
      if (map.containsKey(path[0])) {
        filesSet = (Set)map.get(path[0]);
      }
      else {
        map.put(path[0], filesSet = new HashSet());
      }
      filesSet.add(file);
    }
    // Find common ancestor for each set of files.
    ArrayList ancestorsList = new ArrayList();
    for (Iterator setIterator = map.values().iterator(); setIterator.hasNext();) {
      Set filesSet = (Set)setIterator.next();
      VirtualFile ancestor = null;
      for (Iterator fileIterator = filesSet.iterator(); fileIterator.hasNext();) {
        VirtualFile file = (VirtualFile)fileIterator.next();
        if (ancestor == null) {
          ancestor = file;
          continue;
        }
        ancestor = getCommonAncestor(ancestor, file);
        //assertTrue(ancestor != null);
      }
      ancestorsList.add(ancestor);
      filesSet.clear();
    }
    return (VirtualFile[])ancestorsList.toArray(new VirtualFile[ancestorsList.size()]);
  }

  /**
   * Gets the common ancestor for passed files, or null if the files do not have common ancestors.
   *
   * @param file1 fist file
   * @param file2 second file
   * @return common ancestor for the passed files. Returns <code>null</code> if
   *         the files do not have common ancestor
   */
  public static VirtualFile getCommonAncestor(VirtualFile file1, VirtualFile file2) {
    if (!file1.getFileSystem().equals(file2.getFileSystem())) {
      return null;
    }

    VirtualFile[] path1 = getPathComponents(file1);
    VirtualFile[] path2 = getPathComponents(file2);

    VirtualFile[] minLengthPath;
    VirtualFile[] maxLengthPath;
    if (path1.length < path2.length) {
      minLengthPath = path1;
      maxLengthPath = path2;
    }
    else {
      minLengthPath = path2;
      maxLengthPath = path1;
    }

    int lastEqualIdx = -1;
    for (int i = 0; i < minLengthPath.length; i++) {
      if (minLengthPath[i].equals(maxLengthPath[i])) {
        lastEqualIdx = i;
      }
      else {
        break;
      }
    }
    return (lastEqualIdx != -1) ? minLengthPath[lastEqualIdx] : null;
  }

  /**
   * Gets an array of files representing paths from root to the passed file.
   *
   * @param file the file
   * @return virtual files which represents paths from root to the passed file
   */
  private static VirtualFile[] getPathComponents(VirtualFile file) {
    ArrayList componentsList = new ArrayList();
    while (file != null) {
      componentsList.add(file);
      file = file.getParent();
    }
    int size = componentsList.size();
    VirtualFile components[] = new VirtualFile[size];
    for (int i = 0; i < size; i++) {
      components[i] = (VirtualFile)componentsList.get(size - i - 1);
    }
    return components;
  }

  public static VirtualFile findRelativeFile(String uri, VirtualFile base) {
    if (base != null) {
      if (!base.isValid()){
        LOG.assertTrue(false, "Invalid file name: " + base.getName() + ", url: " + uri);
      }
    }

    uri = ExternalResourceManager.getInstance().getResourceLocation(uri);
    uri = uri.replace('\\', '/');

    if (uri.startsWith("file:///")) {
      uri = uri.substring("file:///".length());
      if (!SystemInfo.isWindows) uri = "/" + uri;
    }
    else if (uri.startsWith("file:/")) {
      uri = uri.substring("file:/".length());
      if (!SystemInfo.isWindows) uri = "/" + uri;
    }
    else if (uri.startsWith("file:")) {
      uri = uri.substring("file:".length());
    }

    VirtualFile file = null;

    if (uri.startsWith("jar:file:/")) {
      uri = uri.substring("jar:file:/".length());
      if (!SystemInfo.isWindows) uri = "/" + uri;
      file = VirtualFileManager.getInstance().findFileByUrl(JarFileSystem.PROTOCOL + ":" + "//" + uri);
    }
    else {
      if (!SystemInfo.isWindows && StringUtil.startsWithChar(uri, '/')) {
        file = LocalFileSystem.getInstance().findFileByPath(uri);
      }
      else if (SystemInfo.isWindows && uri.length() >= 2 && Character.isLetter(uri.charAt(0)) && uri.charAt(1) == ':') {
        file = LocalFileSystem.getInstance().findFileByPath(uri);
      }
    }

    if (file == null) {
      if (base == null) return LocalFileSystem.getInstance().findFileByPath(uri);
      if (!base.isDirectory()) base = base.getParent();
      file = VirtualFileManager.getInstance().findFileByUrl(base.getUrl() + "/" + uri);
      if (file == null) return null;
    }

    return file;
  }

  private static final String FILE = "file";
  private static final String JAR = "jar";
  private static final String MAILTO = "mailto";
  private static final String PROTOCOL_DELIMITER = ":";

  /**
   * Searches for the file specified by given java,net.URL.
   * Note that this method currently tested only for "file" and "jar" protocols under Unix and Windows
   *
   * @param url the URL to find file by
   * @return <code>{@link VirtualFile}</code> if the file was found, <code>null</code> otherwise
   */
  public static VirtualFile findFileByURL(URL url) {
    VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    return findFileByURL(url, virtualFileManager);
  }

  public static VirtualFile findFileByURL(URL url, VirtualFileManager virtualFileManager) {
    String vfUrl = convertFromUrl(url);
    return virtualFileManager.findFileByUrl(vfUrl);
  }

  /**
   * Converts VsfUrl info java.net.URL. Does not support "jar:" protocol.
   *
   * @param vfsUrl VFS url (as constructed by VfsFile.getUrl())
   * @return converted URL or null if error has occured
   */

  public static URL convertToURL(String vfsUrl) {
    if (vfsUrl.startsWith(JAR)) {
      LOG.error("jar: protocol not supported.");
      return null;
    }

    // [stathik] for supporting mail URLs in Plugin Manager
    if (vfsUrl.startsWith(MAILTO)) {
      try {
        return new URL (vfsUrl);
      }
      catch (MalformedURLException e) {
        return null;
      }
    }

    String[] split = vfsUrl.split("://");

    if (split.length != 2) {
      LOG.error("Malformed vfsurl.");
      return null;
    }

    String protocol = split[0];
    String path = split[1];

    try {
      if (protocol.equals(FILE)) {
        return new URL(protocol, "", path);
      }
      else {
        return new URL(vfsUrl);
      }
    }
    catch (MalformedURLException e) {
      LOG.error("MalformedURLException occured:" + e.getMessage());
      return null;
    }
  }

  private static String convertFromUrl(URL url) {
    String protocol = url.getProtocol();
    String path = url.getPath();
    if (protocol.equals(JAR)) {
      if (path.startsWith(FILE + PROTOCOL_DELIMITER)) {
        try {
          URL subURL = new URL(path);
          path = subURL.getPath();
        }
        catch (MalformedURLException e) {
          throw new RuntimeException("Can not parse URL, unhandled exception thrown ", e);
        }
      }
      else {
        throw new RuntimeException(new IOException("Can not parse URL " + url.toExternalForm()));
      }
    }
    if (SystemInfo.isWindows || SystemInfo.isOS2) {
      while (path.charAt(0) == '/') {
        path = path.substring(1, path.length());
      }
    }

    path = StringUtil.replace(path, "%20", " ");
    String vfUrl = protocol + "://" + path;
    return vfUrl;
  }

  public static String urlToPath(String url) {
    if (url == null) return "";
    return VirtualFileManager.extractPath(url);
  }

  public static String pathToUrl(String path) {
    return VirtualFileManager.constructUrl(LocalFileSystem.getInstance().getProtocol(), path);
  }

  public static File virtualToIoFile(VirtualFile file) {
    return new File(PathUtil.toPresentableUrl(file.getUrl()));
  }

  

  public static VirtualFile copyFileRelative(Object requestor, VirtualFile file, VirtualFile toDir, String relativePath) throws IOException {
    StringTokenizer tokenizer = new StringTokenizer(relativePath,"/");
    VirtualFile curDir = toDir;

    while (true) {
      String token = tokenizer.nextToken();
      if (tokenizer.hasMoreTokens()) {
        VirtualFile childDir = toDir.findChild(token);
        if (childDir == null) {
          childDir = curDir.createChildDirectory(requestor, token);
        }
        curDir = childDir;
      }
      else {
        return copyFile(requestor, file, curDir, token);
      }
    }
  }

  public static String fixIDEAUrl( String ideaUrl ) {
    int idx = ideaUrl.indexOf("://");
    if( idx >= 0 ) {
      String s = ideaUrl.substring(0, idx);
      
      if (s.equals(JarFileSystem.PROTOCOL)) {
        s = "jar:file";
      }
      ideaUrl = s+":/"+ideaUrl.substring(idx+3);
    }
    return ideaUrl;
  }

  public static String fixURLforIDEA( String url ) {
    int idx = url.indexOf(":/");
    if( idx >= 0 && url.charAt(idx+2) != '/' ) {
      String prefix = url.substring(0, idx);
      String suffix = url.substring(idx+2);

      if (SystemInfo.isWindows) {
        url = prefix+"://"+suffix;
      } else {
        url = prefix+":///"+suffix;
      }
    }
    return url;
  }

  public static boolean isAncestor(File ancestor, File file, boolean strict) {
    File parent = strict ? file.getParentFile() : file;
    while (parent != null) {
      if (parent.equals(ancestor)) return true;
      parent = parent.getParentFile();
    }

    return false;
  }

  public static Module getModuleForFile(Project project, VirtualFile file){
    return ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(file);
  }
}