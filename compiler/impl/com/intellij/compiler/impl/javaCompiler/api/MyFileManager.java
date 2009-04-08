package com.intellij.compiler.impl.javaCompiler.api;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DefaultFileManager;
import gnu.trove.THashMap;

import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
* @author cdr
*/
class MyFileManager extends DefaultFileManager {
  private final String myOutputDir;
  private final CompAPIDriver myCompAPIDriver;
  private final Map<URI, JavaFileObject> uri2Output = new THashMap<URI, JavaFileObject>();
  private final Map<URI, JavaFileObject> uri2Source = new THashMap<URI, JavaFileObject>();

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
    URI uri = file.toURI();
    JavaFileObject fileObject = uri2Source.get(uri);
    if (fileObject == null) {
      fileObject = new JavaFile(file);
      uri2Source.put(uri, fileObject);
    }
    return fileObject;
  }
  private JavaFileObject getOutput(URI uri) {
    JavaFileObject fileObject = uri2Output.get(uri);
    if (fileObject == null) {
      fileObject = new Output(uri) {
        protected void offerClassFile(URI uri, byte[] bytes) {
          myCompAPIDriver.offer(CompilationEvent.generateClass(uri, bytes));
        }
      };
      uri2Output.put(uri, fileObject);
    }
    return fileObject;
  }

  @Override
  public JavaFileObject getJavaFileForOutput(Location location, String name, JavaFileObject.Kind kind, FileObject fileObject) {
    return getOutput(toURI(myOutputDir,name));
  }

  private static class JavaFile extends SimpleJavaFileObject {
    private final File myFile;

    protected JavaFile(File file) {
      super(file.toURI(), Kind.SOURCE);
      myFile = file;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(myFile);
      if (virtualFile == null) return null;
      return LoadTextUtil.loadText(virtualFile);
    }

    @Override
    public String toString() {
      return toUri().toString();
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

    protected abstract void offerClassFile(URI uri, byte[] bytes);
  }

  private static URI toURI(String outputDir, String name) {
    try {
      return new URI("file:///" + outputDir.replace('\\','/') + "/" + name.replace('.', '/') + JavaFileObject.Kind.CLASS.extension);
    }
    catch (URISyntaxException exception) {
      throw new Error(exception);
    }
  }
}
