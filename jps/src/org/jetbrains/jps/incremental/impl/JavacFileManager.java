package org.jetbrains.jps.incremental.impl;

import org.jetbrains.annotations.NotNull;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.Set;

import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/24/11
 */
class JavacFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {

  private final Context myContext;

  public static interface Context {
    StandardJavaFileManager getStandardFileManager();

    void consumeOutputFile(OutputFileObject obj);

    void reportMessage(final Diagnostic.Kind kind, String message);
  }

  public JavacFileManager(Context context) {
    super(context.getStandardFileManager());
    myContext = context;
  }

  // todo: check if reading source files can be optimized

  public boolean setLocation(Location location, Iterable<? extends File> path) {
    try {
      getStdManager().setLocation(location, path);
    }
    catch (IOException e) {
      myContext.reportMessage(Diagnostic.Kind.ERROR, e.getMessage());
      return false;
    }
    return true;
  }

  public boolean isSameFile(FileObject a, FileObject b) {
    if (a instanceof OutputFileObject && b instanceof OutputFileObject) {
      return a.equals(b);
    }
    return super.isSameFile(a, b);
  }

  public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
    return super.list(location, packageName, kinds, recurse);
  }

  public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
    if (kind != JavaFileObject.Kind.SOURCE && kind != JavaFileObject.Kind.CLASS) {
      throw new IllegalArgumentException("Invalid kind " + kind);
    }
    return getFileForOutput(location, kind, externalizeFileName(className, kind), sibling);
  }

  public FileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
    final StringBuilder name = new StringBuilder();
    if (packageName.isEmpty()) {
      name.append(relativeName);
    }
    else {
      name.append(externalizeFileName(packageName)).append(File.separatorChar).append(relativeName);
    }
    final String fileName = name.toString();
    return getFileForOutput(location, getKind(fileName), fileName, sibling);
  }

  private JavaFileObject getFileForOutput(Location location, JavaFileObject.Kind kind, String fileName, FileObject sibling) throws IOException {
    File dir = null;
    if (location == CLASS_OUTPUT) {
      final File classOutDir = getSingleOutputDirectory(CLASS_OUTPUT);
      if (classOutDir == null) {
        throw new IOException("Output directory is not specified");
      }
      dir = classOutDir;
    }
    else if (location == SOURCE_OUTPUT) {
      final File sourcesOutput = getSingleOutputDirectory(StandardLocation.SOURCE_OUTPUT);
      dir = (sourcesOutput != null ? sourcesOutput : getSingleOutputDirectory(CLASS_OUTPUT));
    }
    else {
      Iterable<? extends File> path = getStdManager().getLocation(location);
      final Iterator<? extends File> it = path.iterator();
      if (it.hasNext()) {
        dir = it.next();
      }
    }
    final File file = (dir == null ? new File(fileName) : new File(dir, fileName));
    return new OutputFileObject(myContext, file, kind);
  }

  private File getSingleOutputDirectory(final Location kind) {
    final Iterable<? extends File> location = getStdManager().getLocation(kind);
    if (location != null) {
      final Iterator<? extends File> it = location.iterator();
      if (it.hasNext()) {
        return it.next();
      }
    }
    return null;
  }

  @NotNull
  public StandardJavaFileManager getStdManager() {
    return fileManager;
  }

  public Iterable<? extends JavaFileObject> toJavaFileObjects(Iterable<? extends File> files) {
    return getStdManager().getJavaFileObjectsFromFiles(files);
  }

  private static URI toURI(String outputDir, String name, JavaFileObject.Kind kind) {
    return createUri("file:///" + outputDir.replace('\\','/') + "/" + name.replace('.', '/') + kind.extension);
  }

  private static URI createUri(String url) {
    return URI.create(url.replaceAll(" ","%20"));
  }

  private static JavaFileObject.Kind getKind(String name) {
    if (name.endsWith(JavaFileObject.Kind.CLASS.extension)){
      return JavaFileObject.Kind.CLASS;
    }
    if (name.endsWith(JavaFileObject.Kind.SOURCE.extension)) {
      return JavaFileObject.Kind.SOURCE;
    }
    if (name.endsWith(JavaFileObject.Kind.HTML.extension)) {
      return JavaFileObject.Kind.HTML;
    }
    return JavaFileObject.Kind.OTHER;
  }

  private static String externalizeFileName(CharSequence cs, JavaFileObject.Kind kind) {
    return externalizeFileName(cs) + kind.extension;
  }

  private static String externalizeFileName(CharSequence name) {
    return name.toString().replace('.', File.separatorChar);
  }

}
