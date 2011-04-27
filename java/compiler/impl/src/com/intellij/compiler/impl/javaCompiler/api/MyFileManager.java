/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.compiler.impl.javaCompiler.api;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.sun.tools.javac.util.ListBuffer;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;

/**
* @author cdr
*/
class MyFileManager implements StandardJavaFileManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.api.MyFileManager");

  private final String myOutputDir;
  private final StandardJavaFileManager myStandardFileManager;
  private final CompAPIDriver myCompAPIDriver;

  MyFileManager(CompAPIDriver compAPIDriver, String outputDir, StandardJavaFileManager standardFileManager) {
    myCompAPIDriver = compAPIDriver;
    myOutputDir = outputDir;
    myStandardFileManager = standardFileManager;
  }

  @Override
  public Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(Iterable<? extends File> files) {
    int size = ((Collection)files).size();
    List<JavaFileObject> result = new ArrayList<JavaFileObject>(size);

    for (File file : files) {
      JavaFileObject fileObject = new JavaVirtualByIoFile(file, JavaFileObject.Kind.SOURCE);
      result.add(fileObject);
    }

    return result;
  }

  @Override
  public JavaFileObject getJavaFileForOutput(Location location, String name, JavaFileObject.Kind kind, FileObject fileObject) {
    URI uri = toURI(myOutputDir, name, kind);
    return new Output(uri, myCompAPIDriver, kind);
  }

  static URI createUri(String url) {
    return URI.create(url.replaceAll(" ","%20"));
  }

  private static URI toURI(String outputDir, String name, JavaFileObject.Kind kind) {
    return createUri("file:///" + outputDir.replace('\\','/') + "/" + name.replace('.', '/') + kind.extension);
  }


  @Override
  public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
    if (recurse) {
      throw new IllegalArgumentException();
    }
    return findInside(location, packageName, kinds, false);
  }

  private List<JavaFileObject> findInside(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean lookForFile)
    throws IOException {
    Iterable<? extends File> path = getLocation(location);
    if (path == null) return Collections.emptyList();

    String subdirectory = packageName.replace('.', '/');
    List<JavaFileObject> results = null;

    for (File directory : path) {
      VirtualFile dir = LocalFileSystem.getInstance().findFileByIoFile(directory);
      if (dir == null) continue;
      if (!dir.isDirectory()) {
        dir = JarFileSystem.getInstance().getJarRootForLocalFile(dir);
        if (dir == null) continue;
      }
      VirtualFile virtualFile = StringUtil.isEmptyOrSpaces(subdirectory) ? dir : dir.findFileByRelativePath(subdirectory);
      if (virtualFile == null) continue;

      VirtualFile[] children;
      if (lookForFile) {
        if (!virtualFile.isDirectory()) {
          children = new VirtualFile[]{virtualFile};
        }
        else continue;
      }
      else {
        children = virtualFile.getChildren();
      }
      for (VirtualFile child : children) {
        JavaFileObject.Kind kind = getKind("." + child.getExtension());
        if (kinds == null || kinds.contains(kind)) {
          if (results == null) results = new SmartList<JavaFileObject>();
          if (kind == JavaFileObject.Kind.SOURCE && child.getFileSystem() instanceof JarFileSystem) continue;  //for some reasdon javac looks for java files inside jar

          // do not use VFS to read .class content
          JavaFileObject fileObject =
            kind == JavaFileObject.Kind.CLASS && child.getFileSystem() == LocalFileSystem.getInstance() ?
            new JavaIoFile(new File(child.getPath()), kind) : new JavaVirtualFile(child, kind);
          results.add(fileObject);
        }
      }
    }

    List<JavaFileObject> ret = results == null ? Collections.<JavaFileObject>emptyList() : results;

    if (LOG.isDebugEnabled()) {
      // for testing consistency
      Collection c = (Collection)myStandardFileManager.list(location, packageName, kinds, false);
      Collection<JavaFileObject> sup = new HashSet(c);
      assert sup.size() == c.size();
      assert new HashSet(c).equals(sup);

      THashSet<JavaFileObject> s = new THashSet<JavaFileObject>(new TObjectHashingStrategy<JavaFileObject>() {
        public int computeHashCode(JavaFileObject object) {
          return object.getName().hashCode();
        }

        public boolean equals(JavaFileObject o1, JavaFileObject o2) {
          return o1.getKind() == o2.getKind() && o1.toUri().equals(o2.toUri());
        }
      });
      s.addAll(ret);

      s.removeAll(sup);
      if (ret.size() != sup.size()) {
        assert false : "our implementation differs from javac'";
      }
    }

    return ret;
  }

  private static JavaFileObject.Kind getKind(String name) {
      if (name.endsWith(JavaFileObject.Kind.CLASS.extension))
          return JavaFileObject.Kind.CLASS;
      else if (name.endsWith(JavaFileObject.Kind.SOURCE.extension))
          return JavaFileObject.Kind.SOURCE;
      else if (name.endsWith(JavaFileObject.Kind.HTML.extension))
          return JavaFileObject.Kind.HTML;
      else
          return JavaFileObject.Kind.OTHER;
  }


  @Override
  public String inferBinaryName(Location location, JavaFileObject file) {
    return FileUtil.getNameWithoutExtension(new File(file.getName()).getName());
  }

  ////////// delegates
  @Override
  public int isSupportedOption(String option) {
    return myStandardFileManager.isSupportedOption(option);
  }

  @Override
  public void close() throws IOException {
    myStandardFileManager.close();
  }

  @Override
  public void flush() throws IOException {
    myStandardFileManager.flush();
  }

  @Override
  public boolean handleOption(String current, Iterator<String> remaining) {
    return myStandardFileManager.handleOption(current, remaining);
  }

  @Override
  public ClassLoader getClassLoader(Location location) {
    return myStandardFileManager.getClassLoader(location);
  }

  @Override
  public boolean isSameFile(FileObject a, FileObject b) {
    if ((a instanceof FileVirtualObject && b instanceof FileVirtualObject) || (a instanceof Output && b instanceof Output)) {
      return a.equals(b);
    }
    return myStandardFileManager.isSameFile(a, b);
  }

  public Iterable<? extends JavaFileObject> getJavaFileObjects(File... files) {
    return getJavaFileObjectsFromFiles(Arrays.asList(files));
  }

  public Iterable<? extends JavaFileObject> getJavaFileObjects(String... names) {
    return getJavaFileObjectsFromStrings(Arrays.asList(names));
  }
  public Iterable<? extends JavaFileObject> getJavaFileObjectsFromStrings(Iterable<String> names) {
    ListBuffer<File> files = new ListBuffer<File>();
    for (String name : names) {
      files.append(new File(name));
    }
    return getJavaFileObjectsFromFiles(files.toList());
  }

  public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) throws IOException {
    List<JavaFileObject> result = findInside(location, className, kind == null ? null : Collections.singleton(kind), true);
    if (!result.isEmpty()) {
      return result.get(0);
    }
    return null;
  }

  @Override
  public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
    return getJavaFileForInput(location, packageName + "/" + relativeName, getKind(relativeName));
  }

  @Override
  public FileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
    return getJavaFileForOutput(location, packageName + "/" + relativeName, getKind(relativeName), null);
  }

  @Override
  public Iterable<? extends File> getLocation(Location location) {
    return myStandardFileManager.getLocation(location);
  }

  @Override
  public void setLocation(Location location, Iterable<? extends File> path) throws IOException {
    myStandardFileManager.setLocation(location, path);
  }

  @Override
  public boolean hasLocation(Location location) {
    return myStandardFileManager.hasLocation(location);
  }
}
