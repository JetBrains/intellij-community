/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.javac;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.PathUtils;
import org.jetbrains.jps.builders.java.JavaSourceTransformer;

import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
class JavacFileManager extends JpsJavacFileManager {

  private final Collection<? extends JavaSourceTransformer> mySourceTransformers;
  @Nullable
  private String myEncodingName;

  JavacFileManager(Context context, Collection<? extends JavaSourceTransformer> transformers) {
    super(context);
    mySourceTransformers = transformers;
  }

  @Override
  public boolean handleOption(String current, final Iterator<String> remaining) {
    if ("-encoding".equalsIgnoreCase(current) && remaining.hasNext()) {
      final String encoding = remaining.next();
      myEncodingName = encoding;
      return super.handleOption(current, new Iterator<String>() {
        private boolean encodingConsumed = false;
        @Override
        public boolean hasNext() {
          return !encodingConsumed || remaining.hasNext();
        }

        @Override
        public String next() {
          if (!encodingConsumed) {
            encodingConsumed = true;
            return encoding;
          }
          return remaining.next();
        }

        @Override
        public void remove() {
          if (encodingConsumed) {
            remaining.remove();
          }
        }
      });
    }
    return super.handleOption(current, remaining);
  }

  @Override
  public String inferBinaryName(Location location, JavaFileObject file) {
    return super.inferBinaryName(location, unwrapFileObject(file));
  }

  @Override
  public void setLocation(Location location, Iterable<? extends File> path) throws IOException{
    getStdManager().setLocation(location, path);
  }

  @Override
  public Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(Iterable<? extends File> files) {
    return wrapJavaFileObjects(getStdManager().getJavaFileObjectsFromFiles(files));
  }

  @Override
  public Iterable<? extends JavaFileObject> getJavaFileObjects(File... files) {
    return wrapJavaFileObjects(getStdManager().getJavaFileObjects(files));
  }

  @Override
  public Iterable<? extends JavaFileObject> getJavaFileObjectsFromStrings(Iterable<String> names) {
    return wrapJavaFileObjects(getStdManager().getJavaFileObjectsFromStrings(names));
  }

  @Override
  public Iterable<? extends JavaFileObject> getJavaFileObjects(String... names) {
    return wrapJavaFileObjects(getStdManager().getJavaFileObjects(names));
  }

  @Override
  public Iterable<? extends File> getLocation(Location location) {
    return getStdManager().getLocation(location);
  }

  @Override
  public boolean isSameFile(FileObject a, FileObject b) {
    if (a instanceof OutputFileObject || b instanceof OutputFileObject) {
      return a.equals(b);
    }
    return super.isSameFile(unwrapFileObject(a), unwrapFileObject(b));
  }

  private static FileObject unwrapFileObject(FileObject a) {
    return a instanceof TransformableJavaFileObject ? ((TransformableJavaFileObject)a).getOriginal() : a;
  }

  private static JavaFileObject unwrapFileObject(JavaFileObject a) {
    return a instanceof TransformableJavaFileObject ? ((TransformableJavaFileObject)a).getOriginal() : a;
  }

  @Override
  public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
    checkCanceled();
    final FileObject fo = super.getFileForInput(location, packageName, relativeName);
    if (fo == null) {
      // workaround javac bug (missing null-check): throwing exception here instead of returning null
      throw new FileNotFoundException("Resource does not exist : " + location + '/' + packageName + '/' + relativeName);
    }
    return fo;
  }

  @Override
  public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
    final Iterable<JavaFileObject> objects = super.list(location, packageName, kinds, recurse);
    //noinspection unchecked
    return kinds.contains(JavaFileObject.Kind.SOURCE)? (Iterable<JavaFileObject>)wrapJavaFileObjects(objects) : objects;
  }

  private static <T> List<T> toList(Iterable<T> iterable) {
    final List<T> list = new ArrayList<T>();
    for (T t : iterable) {
      list.add(t);
    }
    return list;
  }

  private Iterable<? extends JavaFileObject> wrapJavaFileObjects(Iterable<? extends JavaFileObject> originalObjects) {
    if (mySourceTransformers.isEmpty()) {
      return originalObjects;
    }
    List<JavaFileObject> wrapped = null;
    for (JavaFileObject fo : originalObjects) {
      if (wrapped == null) {
        wrapped = new ArrayList<JavaFileObject>(); // lazy init
      }
      wrapped.add(JavaFileObject.Kind.SOURCE.equals(fo.getKind())? new TransformableJavaFileObject(fo, mySourceTransformers) : fo);
    }
    return wrapped != null? wrapped : originalObjects;
  }

  @Override
  public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) throws IOException {
    checkCanceled();
    final JavaFileObject fo = super.getJavaFileForInput(location, className, kind);
    if (fo == null && !"module-info".equals(className)) {
      // workaround javac bug (missing null-check): throwing exception here instead of returning null
      throw new FileNotFoundException("Java resource does not exist : " + location + '/' + kind + '/' + className);
    }
    return mySourceTransformers.isEmpty()? fo : new TransformableJavaFileObject(fo, mySourceTransformers);
  }

  @Override
  public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
    if (kind != JavaFileObject.Kind.SOURCE && kind != JavaFileObject.Kind.CLASS) {
      throw new IllegalArgumentException("Invalid kind " + kind);
    }
    return getFileForOutput(location, kind, externalizeFileName(className, kind), className, sibling);
  }

  @Override
  public FileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
    final StringBuilder name = new StringBuilder();
    if (packageName.isEmpty()) {
      name.append(relativeName);
    }
    else {
      name.append(externalizeFileName(packageName)).append(File.separatorChar).append(relativeName);
    }
    final String fileName = name.toString();
    return getFileForOutput(location, getKind(fileName), fileName, null, sibling);
  }

  private OutputFileObject getFileForOutput(Location location, JavaFileObject.Kind kind, String fileName, @Nullable String className, FileObject sibling) throws IOException {
    checkCanceled();

    JavaFileObject src = null;
    if (sibling instanceof JavaFileObject) {
      final JavaFileObject javaFileObject = (JavaFileObject)sibling;
      if (javaFileObject.getKind() == JavaFileObject.Kind.SOURCE) {
        src = javaFileObject;
      }
    }

    File dir = getSingleOutputDirectory(location, src);

    if (location == StandardLocation.CLASS_OUTPUT) {
      if (dir == null) {
        throw new IOException("Output directory is not specified");
      }
    }
    else if (location == StandardLocation.SOURCE_OUTPUT) {
      if (dir == null) {
        dir = getSingleOutputDirectory(StandardLocation.CLASS_OUTPUT, src);
        if (dir == null) {
          throw new IOException("Neither class output directory nor source output are specified");
        }
      }
    }
    final File file = (dir == null? new File(fileName).getAbsoluteFile() : new File(dir, fileName));
    return new OutputFileObject(myContext, dir, fileName, file, kind, className, src != null? src.toUri() : null, myEncodingName);
  }

  @Override
  public ClassLoader getClassLoader(Location location) {
    final Iterable<? extends File> path = getLocation(location);
    if (path == null) {
      return null;
    }
    final List<URL> urls = new ArrayList<URL>();
    for (File f: path) {
      try {
        urls.add(f.toURI().toURL());
      }
      catch (MalformedURLException e) {
        throw new AssertionError(e);
      }
    }
    // ensure processor's loader will not resolve against JPS classes and libraries used in JPS
    return new URLClassLoader(urls.toArray(new URL[0]), myContext.getStandardFileManager().getClass().getClassLoader());
  }

  private File getSingleOutputDirectory(final Location loc, final JavaFileObject sourceFile) {
    if (loc == StandardLocation.CLASS_OUTPUT) {
      if (myOutputsMap.size() > 1 && sourceFile != null) {
        // multiple outputs case
        final File outputDir = findOutputDir(PathUtils.convertToFile(sourceFile.toUri()));
        if (outputDir != null) {
          return outputDir;
        }
      }
    }

    final Iterable<? extends File> location = getStdManager().getLocation(loc);
    if (location != null) {
      final Iterator<? extends File> it = location.iterator();
      if (it.hasNext()) {
        return it.next();
      }
    }
    return null;
  }

  private File findOutputDir(File src) {
    File file = FileUtilRt.getParentFile(src);
    while (file != null) {
      for (Map.Entry<File, Set<File>> entry : myOutputsMap.entrySet()) {
        if (entry.getValue().contains(file)) {
          return entry.getKey();
        }
      }
      file = FileUtilRt.getParentFile(file);
    }
    return null;
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

  private int myChecksCounter = 0;

  private void checkCanceled() {
    final int counter = (myChecksCounter + 1) % 10;
    myChecksCounter = counter;
    if (counter == 0 && myContext.isCanceled()) {
      throw new CompilationCanceledException();
    }
  }
}
