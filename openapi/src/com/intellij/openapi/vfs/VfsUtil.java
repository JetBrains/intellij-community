/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.StringTokenizer;

public class VfsUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.VfsUtil");

  public static String loadText(VirtualFile file) throws IOException{
    InputStreamReader reader = new InputStreamReader(file.getInputStream(), file.getCharset());
    try {
      return new String(FileUtil.loadText(reader, (int)file.getLength()));
    }
    finally {
      reader.close();
    }
  }

  public static void saveText(VirtualFile file, String text) throws IOException {
    file.setBinaryContent(text.getBytes(file.getCharset().name()));
  }

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
  public static boolean isAncestor(@NotNull VirtualFile ancestor, @NotNull VirtualFile file, boolean strict) {
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
    for (VirtualFile child : children) {
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
    final VirtualFile newChild = toDir.createChildData(requestor, newName);
    // [jeka] TODO: to be duscussed if the copy should have the same timestamp as the original
    //OutputStream out = newChild.getOutputStream(requestor, -1, file.getActualTimeStamp());
    newChild.setBinaryContent(file.contentsToByteArray());
    return newChild;
  }

  /**
   * Copies content of resource to the given file
   *
   * @param file to copy to
   * @param resourceUrl url of the resource to be copied
   * @throws java.io.IOException if resource not found or copying failed
   */
  public static void copyFromResource(VirtualFile file, @NonNls String resourceUrl) throws IOException {
    InputStream out = VfsUtil.class.getResourceAsStream(resourceUrl);
    if (out == null) {
      throw new FileNotFoundException(resourceUrl);
    }
    try {
      byte[] bytes = FileUtil.adaptiveLoadBytes(out);
      file.setBinaryContent(bytes);
    } finally {
      out.close();
    }
  }

  /**
   * Gets the array of common ancestors for passed files.
   *
   * @param files array of files
   * @return array of common ancestors for passed files
   */
  public static VirtualFile[] getCommonAncestors(VirtualFile[] files) {
    // Separate files by first component in the path.
    HashMap<VirtualFile,Set<VirtualFile>> map = new HashMap<VirtualFile, Set<VirtualFile>>();
    for (VirtualFile aFile : files) {
      VirtualFile directory = aFile.isDirectory() ? aFile : aFile.getParent();
      if (directory == null) return VirtualFile.EMPTY_ARRAY;
      VirtualFile[] path = getPathComponents(directory);
      Set<VirtualFile> filesSet;
      final VirtualFile firstPart = path[0];
      if (map.containsKey(firstPart)) {
        filesSet = map.get(firstPart);
      }
      else {
        filesSet = new THashSet<VirtualFile>();
        map.put(firstPart, filesSet);
      }
      filesSet.add(directory);
    }
    // Find common ancestor for each set of files.
    ArrayList<VirtualFile> ancestorsList = new ArrayList<VirtualFile>();
    for (Set<VirtualFile> filesSet : map.values()) {
      VirtualFile ancestor = null;
      for (VirtualFile file : filesSet) {
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
    return ancestorsList.toArray(new VirtualFile[ancestorsList.size()]);
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
    return lastEqualIdx != -1 ? minLengthPath[lastEqualIdx] : null;
  }

  /**
   * Gets an array of files representing paths from root to the passed file.
   *
   * @param file the file
   * @return virtual files which represents paths from root to the passed file
   */
  private static VirtualFile[] getPathComponents(VirtualFile file) {
    ArrayList<VirtualFile> componentsList = new ArrayList<VirtualFile>();
    while (file != null) {
      componentsList.add(file);
      file = file.getParent();
    }
    int size = componentsList.size();
    VirtualFile[] components = new VirtualFile[size];
    for (int i = 0; i < size; i++) {
      components[i] = componentsList.get(size - i - 1);
    }
    return components;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static VirtualFile findRelativeFile(String uri, VirtualFile base) {
    if (base != null) {
      if (!base.isValid()){
        LOG.error("Invalid file name: " + base.getName() + ", url: " + uri);
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
      if (base == null) return LocalFileSystem.getInstance().findFileByPath(uri);
      file = VirtualFileManager.getInstance().findFileByUrl(base.getUrl() + "/" + uri);
      if (file == null) return null;
    }

    return file;
  }

  @NonNls private static final String FILE = "file";
  @NonNls private static final String JAR = "jar";
  @NonNls private static final String MAILTO = "mailto";
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
          throw new RuntimeException(VfsBundle.message("url.parse.unhandled.exception"), e);
        }
      }
      else {
        throw new RuntimeException(new IOException(VfsBundle.message("url.parse.error", url.toExternalForm())));
      }
    }
    if (SystemInfo.isWindows || SystemInfo.isOS2) {
      while (path.charAt(0) == '/') {
        path = path.substring(1, path.length());
      }
    }

    path = StringUtil.replace(path, "%20", " ");
    return protocol + "://" + path;
  }

  public static String urlToPath(String url) {
    if (url == null) return "";
    return VirtualFileManager.extractPath(url);
  }

  public static String pathToUrl(@NotNull String path) {
    return VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, path);
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
        VirtualFile childDir = curDir.findChild(token);
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
        //noinspection HardCodedStringLiteral
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

  @Nullable
  public static Module getModuleForFile(@NotNull Project project, @NotNull VirtualFile file){
    return ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(file);
  }

  public static String calcRelativeToProjectPath(final VirtualFile file, final Project project) {
    String url = file.getPresentableUrl();
    if (project == null) {
      return url;
    }
    else {
      final VirtualFile projectFile = project.getProjectFile();
      if (projectFile != null) {
        //noinspection ConstantConditions
        final String projectHomeUrl = projectFile.getParent().getPresentableUrl();
        if (url.startsWith(projectHomeUrl)) {
          url = "..." + url.substring(projectHomeUrl.length());
        }
      }
      final Module module = getModuleForFile(project, file);
      if (module == null) return url;
      return new StringBuffer().append("[").append(module.getName()).append("] - ").append(url).toString();
    }
  }

  /**
   * Returns the relative path from one virtual file to another.
   *
   * @param src           the file from which the relative path is built.
   * @param dst           the file to which the path is built.
   * @param separatorChar the separator for the path components.
   * @return the relative path, or null if the files have no common ancestor.
   * @since 5.0.2
   */

  @Nullable
  public static String getPath(VirtualFile src, VirtualFile dst, char separatorChar) {
    final VirtualFile commonAncestor = getCommonAncestor(src, dst);
    if (commonAncestor != null) {
      StringBuffer buffer = new StringBuffer();
      if (src != commonAncestor) {
        while (src.getParent() != commonAncestor) {
          buffer.append("..").append(separatorChar);
          src = src.getParent();
          assert src != null;
        }
      }
      buffer.append(getRelativePath(dst, commonAncestor, separatorChar));
      return buffer.toString();
    }

    return null;
  }

  public static boolean isValidName(String name) {
    if (name.indexOf('\\') >= 0) return false;
    return name.indexOf('/') < 0;
  }

  public static String getUrlForLibraryRoot(File libraryRoot) {
    String path = FileUtil.toSystemIndependentName(libraryRoot.getAbsolutePath());
    if (FileTypeManager.getInstance().getFileTypeByFileName(libraryRoot.getName()) == StdFileTypes.ARCHIVE) {
      return VirtualFileManager.constructUrl(JarFileSystem.getInstance().getProtocol(), path + JarFileSystem.JAR_SEPARATOR);
    }
    else {
      return VirtualFileManager.constructUrl(LocalFileSystem.getInstance().getProtocol(), path);
    }
  }

  public static VirtualFile createChildSequent(Object requestor, VirtualFile dir, String prefix, String extension) throws IOException {
    String fileName = prefix + "." + extension;
    int i = 1;
    while (dir.findChild(fileName) != null) {
      fileName = prefix + i + "." + extension;
      i++;
    }
    return dir.createChildData(requestor, fileName);
  }
}
