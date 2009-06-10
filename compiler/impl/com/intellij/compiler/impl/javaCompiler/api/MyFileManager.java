package com.intellij.compiler.impl.javaCompiler.api;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DefaultFileManager;

import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;

/**
* @author cdr
*/
class MyFileManager extends DefaultFileManager {
  private final String myOutputDir;
  private final CompAPIDriver myCompAPIDriver;

  MyFileManager(CompAPIDriver compAPIDriver, String outputDir) {
    super(new Context(), false, null);
    myCompAPIDriver = compAPIDriver;
    myOutputDir = outputDir;
  }

  @Override
  public Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(Iterable<? extends File> files) {
    //return super.getJavaFileObjectsFromFiles(files);
    int size = ((Collection)files).size();
    List<JavaFileObject> result = new ArrayList<JavaFileObject>(size);

    for (File file : files) {
      result.add(getSource(file));
    }

    return result;
  }

  private JavaFileObject getSource(File file) {
    JavaFileObject fileObject = new JavaIoFile(file);
    return fileObject;
  }
  private JavaFileObject getOutput(URI uri) {
    JavaFileObject fileObject = new Output(uri) {
      protected void offerClassFile(URI uri, byte[] bytes) {
        myCompAPIDriver.offer(CompilationEvent.generateClass(uri, bytes));
      }
    };
    return fileObject;
  }

  @Override
  public JavaFileObject getJavaFileForOutput(Location location, String name, JavaFileObject.Kind kind, FileObject fileObject) {
    return getOutput(toURI(myOutputDir,name));
  }

  private static class JavaIoFile extends FileVirtualObject {
    private final File myFile;

    protected JavaIoFile(File file) {
      super(file.toURI(), Kind.SOURCE);
      myFile = file;
    }

    protected VirtualFile getVirtualFile() {
      return LocalFileSystem.getInstance().findFileByIoFile(myFile);
    }
  }
  
  private static class JavaVirtualFile extends FileVirtualObject {
    private final VirtualFile myFile;

    protected JavaVirtualFile(VirtualFile file, Kind source) {
      super(URI.create(file.getUrl()), source);
      myFile = file;
    }

    protected VirtualFile getVirtualFile() {
      return myFile;
    }
  }

  private abstract static class Output extends SimpleJavaFileObject {
    private Output(URI uri) {
      super(uri, Kind.CLASS);
    }

    @Override
    public ByteArrayOutputStream openOutputStream() {
      return new ByteArrayOutputStream() {
        @Override
        public void close() throws IOException {
          super.close();
          offerClassFile(toUri(), toByteArray());
        }
      };
    }
    @Override
    public int hashCode() {
      return toUri().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof JavaFileObject && toUri().equals(((JavaFileObject)obj).toUri());
    }

    protected abstract void offerClassFile(URI uri, byte[] bytes);
  }

  private static URI toURI(String outputDir, String name) {
    return URI.create("file:///" + outputDir.replace('\\','/') + "/" + name.replace('.', '/') + JavaFileObject.Kind.CLASS.extension);
  }

  @Override
  public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
    if (/*location != StandardLocation.SOURCE_PATH || */recurse) {
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
          results.add(new JavaVirtualFile(child, kind));
        }
      }
    }
    //if (this!=null) return c;

    List<JavaFileObject> ret = results == null ? Collections.<JavaFileObject>emptyList() : results;
    //Collection c = (Collection)super.list(location, packageName, kinds, recurse);
    //Collection<JavaFileObject> sup = new HashSet(c);
    //assert sup.size() == c.size();
    //assert new HashSet(c).equals(sup);
    //
    //THashSet<JavaFileObject> s = new THashSet<JavaFileObject>(new TObjectHashingStrategy<JavaFileObject>() {
    //  public int computeHashCode(JavaFileObject object) {
    //    return object.getName().hashCode();
    //  }
    //
    //  public boolean equals(JavaFileObject o1, JavaFileObject o2) {
    //    return o1.getKind() == o2.getKind() && o1.toUri().equals(o2.toUri());
    //  }
    //});
    //s.addAll(ret);
    //
    //s.removeAll(sup);
    //if (ret.size() != sup.size()) {
    //  int i = 0;
    //}
    return ret;
  }

  @Override
  public String inferBinaryName(Location location, JavaFileObject file) {
    return FileUtil.getNameWithoutExtension(new File(file.getName()).getName());
  }
}
