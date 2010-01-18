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
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DefaultFileManager;
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
@SuppressWarnings({"Since15"})
class MyFileManager extends DefaultFileManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.api.MyFileManager");

  private final String myOutputDir;
  private final CompAPIDriver myCompAPIDriver;

  MyFileManager(CompAPIDriver compAPIDriver, String outputDir) {
    super(new Context(), false, null);
    myCompAPIDriver = compAPIDriver;
    myOutputDir = outputDir;
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
    URI uri = toURI(myOutputDir, name);
    return new Output(uri, myCompAPIDriver);
  }

  static URI createUri(String url) {
    return URI.create(url.replaceAll(" ","%20"));
  }

  private static URI toURI(String outputDir, String name) {
    return createUri("file:///" + outputDir.replace('\\','/') + "/" + name.replace('.', '/') + JavaFileObject.Kind.CLASS.extension);
  }

  @Override
  public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
    if (recurse) {
      return super.list(location, packageName, kinds, recurse);
    }
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
      
      if (!virtualFile.isDirectory()) continue;
      for (VirtualFile child : virtualFile.getChildren()) {
        JavaFileObject.Kind kind = getKind("."+child.getExtension());
        if (kinds.contains(kind)) {
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
      Collection c = (Collection)super.list(location, packageName, kinds, recurse);
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

  @Override
  public String inferBinaryName(Location location, JavaFileObject file) {
    return FileUtil.getNameWithoutExtension(new File(file.getName()).getName());
  }
}
