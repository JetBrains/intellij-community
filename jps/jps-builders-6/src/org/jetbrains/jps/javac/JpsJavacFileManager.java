// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.PathUtils;
import org.jetbrains.jps.builders.java.JavaSourceTransformer;

import javax.tools.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 * Date: 01-Oct-18
 */
public class JpsJavacFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> implements StandardJavaFileManager {
  private static final String _OS_NAME = System.getProperty("os.name").toLowerCase(Locale.US);
  private static final boolean isWindows = _OS_NAME.startsWith("windows");
  private static final boolean isOS2 = _OS_NAME.startsWith("os/2") || _OS_NAME.startsWith("os2");
  private static final boolean isMac = _OS_NAME.startsWith("mac");
  private static final boolean isFileSystemCaseSensitive = !isWindows && !isOS2 && !isMac;
  private static final Set<JavaFileObject.Kind> ourSourceOrClass = EnumSet.of(JavaFileObject.Kind.SOURCE, JavaFileObject.Kind.CLASS);
  private static final Set<StandardLocation> ourFSLocations = EnumSet.of(
    StandardLocation.PLATFORM_CLASS_PATH,
    StandardLocation.CLASS_OUTPUT,
    StandardLocation.CLASS_PATH,
    StandardLocation.SOURCE_OUTPUT,
    StandardLocation.SOURCE_PATH,
    StandardLocation.ANNOTATION_PROCESSOR_PATH
  );
  private final Context myContext;
  private final boolean myJavacBefore9;
  private final Collection<JavaSourceTransformer> mySourceTransformers;
  private final FileOperations myFileOperations = new DefaultFileOperations();

  private final Function<File, JavaFileObject> myFileToInputFileObjectConverter = new Function<File, JavaFileObject>() {
    @Override
    public JavaFileObject fun(File file) {
      return new InputFileObject(file, myEncodingName);
    }
  };
  protected Map<File, Set<File>> myOutputsMap = Collections.emptyMap();
  @Nullable
  private String myEncodingName;
  private int myChecksCounter = 0;

  public JpsJavacFileManager(final Context context, boolean javacBefore9, Collection<JavaSourceTransformer> transformers) {
    super(context.getStandardFileManager());
    myJavacBefore9 = javacBefore9;
    mySourceTransformers = transformers;
    myContext = new Context() {
      @Override
      public boolean isCanceled() {
        return context.isCanceled();
      }

      @NotNull
      @Override
      public StandardJavaFileManager getStandardFileManager() {
        return context.getStandardFileManager();
      }

      @Override
      public void consumeOutputFile(@NotNull OutputFileObject obj) {
        try {
          context.consumeOutputFile(obj);
        }
        finally {
          onOutputFileGenerated(obj.getFile());
        }
      }

      @Override
      public void reportMessage(Diagnostic.Kind kind, String message) {
        context.reportMessage(kind, message);
      }
    };
  }

  private Iterable<? extends JavaFileObject> wrapJavaFileObjects(final Iterable<? extends JavaFileObject> originalObjects) {
    return mySourceTransformers.isEmpty()? originalObjects : convert(originalObjects, new Function<JavaFileObject, JavaFileObject>() {
      @Override
      public JavaFileObject fun(JavaFileObject fo) {
        return JavaFileObject.Kind.SOURCE.equals(fo.getKind())? new TransformableJavaFileObject(fo, mySourceTransformers) : fo;
      }
    });
  }

  @Override
  public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) throws IOException {
    checkCanceled();
    if (!ourSourceOrClass.contains(kind)) {
      throw new IllegalArgumentException("Invalid kind: " + kind);
    }
    final JavaFileObject fo = super.getJavaFileForInput(location, className, kind); // todo
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

  private void checkCanceled() {
    final int counter = (myChecksCounter + 1) % 10;
    myChecksCounter = counter;
    if (counter == 0 && myContext.isCanceled()) {
      throw new CompilationCanceledException();
    }
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

  interface Context {
    boolean isCanceled();

    @NotNull
    StandardJavaFileManager getStandardFileManager();

    void consumeOutputFile(@NotNull OutputFileObject obj);

    void reportMessage(final Diagnostic.Kind kind, String message);
  }

  public final Context getContext() {
    return myContext;
  }

  @NotNull
  protected StandardJavaFileManager getStdManager() {
    return fileManager;
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
    final JavaFileObject _fo = unwrapFileObject(file);
    if (_fo instanceof JpsFileObject) {
      final String inferred = ((JpsFileObject)_fo).inferBinaryName(getLocation(location), isFileSystemCaseSensitive);
      if (inferred != null) {
        return inferred;
      }
    }
    return super.inferBinaryName(location, _fo);
  }

  @Override
  public void setLocation(Location location, Iterable<? extends File> path) throws IOException{
    getStdManager().setLocation(location, path);
  }

  @Override
  public Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(final Iterable<? extends File> files) {
    return wrapJavaFileObjects(convert(files, new Function<File, JavaFileObject>() {
      @Override
      public JavaFileObject fun(File file) {
        return new InputFileObject(file, myEncodingName);
      }
    }));
  }

  @Override
  public Iterable<? extends JavaFileObject> getJavaFileObjects(File... files) {
    return getJavaFileObjectsFromFiles(Arrays.asList(files));
  }

  @Override
  public Iterable<? extends JavaFileObject> getJavaFileObjectsFromStrings(final Iterable<String> names) {
    return getJavaFileObjectsFromFiles(convert(names, new Function<String, File>() {
      @Override
      public File fun(String s) {
        return new File(s);
      }
    }));
  }

  @Override
  public Iterable<? extends JavaFileObject> getJavaFileObjects(String... names) {
    return getJavaFileObjectsFromStrings(Arrays.asList(names));
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

  private boolean isFileSystemLocation(Location location) {
    try {
      final StandardLocation loc = StandardLocation.valueOf(location.getName());
      if (loc == StandardLocation.PLATFORM_CLASS_PATH) {
        return myJavacBefore9;
      }
      return ourFSLocations.contains(loc);
    }
    catch (IllegalArgumentException ignored) {
      return false; // assume 'unknown' location is a non-FS location
    }
  }

  @Override
  public Iterable<JavaFileObject> list(Location location, String packageName, final Set<JavaFileObject.Kind> kinds, final boolean recurse) throws IOException {
    Iterable<JavaFileObject> allFiles;
    try {
      if (isFileSystemLocation(location)) {
        // we consider here only locations that are known to be file-based
        final Iterable<? extends File> locationRoots = getLocation(location);
        if (locationRoots == null) {
          return Collections.emptyList();
        }

        final List<Iterable<JavaFileObject>> result = new ArrayList<Iterable<JavaFileObject>>();
        for (File root : locationRoots) {
          final boolean isFile;

          FileOperations.Archive archive = myFileOperations.lookupArchive(root);
          if (archive != null) {
            isFile = true;
          }
          else {
            isFile = myFileOperations.isFile(root);
          }

          if (isFile) {
            // Not a directory; either a file or non-existent, create the archive
            try {
              if (archive == null) {
                archive = myFileOperations.openArchive(root, myEncodingName);
              }
              if (archive != null) {
                result.add(archive.list(packageName.replace('.', '/'), kinds, recurse));
              }
              else {
                // fallback to default implementation
                result.add(super.list(location, packageName, kinds, recurse));
              }
            }
            catch (IOException ex) {
              throw new IOException("Error reading file " + root + ": " + ex.getMessage(), ex);
            }
          }
          else {
            // is a directory or does not exist
            final File dir = new File(root, packageName.replace('.', '/'));
            final BooleanFunction<File> filter = recurse?
              new BooleanFunction<File>() {
                @Override
                public boolean fun(File file) {
                  return kinds.contains(getKind(file.getName()));
                }
              }:
              new BooleanFunction<File>() {
                final boolean acceptUnknownFiles = kinds.contains(JavaFileObject.Kind.OTHER);
                @Override
                public boolean fun(File file) {
                  return kinds.contains(getKind(file.getName())) && (!acceptUnknownFiles || myFileOperations.isFile(file));
                }
              };
            result.add(convert(filter(myFileOperations.listFiles(dir, recurse), filter), myFileToInputFileObjectConverter));
          }
        }
        allFiles = merge(result);
      }
      else {
        // locations, not supported by this class should be handled by default javac file manager
        allFiles = super.list(location, packageName, kinds, recurse);
      }
    }
    catch (IllegalStateException e) {
      if (e.getCause() instanceof UnsupportedOperationException) {
        // fallback
        allFiles = super.list(location, packageName, kinds, recurse);
      }
      else {
        throw e;
      }
    }
    catch (UnsupportedOperationException e) {
      // fallback
      allFiles = super.list(location, packageName, kinds, recurse);
    }
    //noinspection unchecked
    return kinds.contains(JavaFileObject.Kind.SOURCE) ? (Iterable<JavaFileObject>)wrapJavaFileObjects(allFiles) : allFiles;
  }

  public void onOutputFileGenerated(File file) {
    final File parent = file.getParentFile();
    if (parent != null) {
      myFileOperations.clearCaches(parent);
    }
  }

  @Override
  public void close() {
    try {
      super.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally {
      myOutputsMap = Collections.emptyMap();
      myFileOperations.clearCaches(null);
    }
  }

  public void setOutputDirectories(final Map<File, Set<File>> outputDirToSrcRoots) throws IOException {
    for (File outputDir : outputDirToSrcRoots.keySet()) {
      // this will validate output dirs
      setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(outputDir));
    }
    myOutputsMap = outputDirToSrcRoots;
  }

  public static <T> Iterable<T> merge(final Iterable<T> first, final Iterable<T> second) {
    return new Iterable<T>() {
      @Override
      @NotNull
      public Iterator<T> iterator() {
        final Iterator<T> i1 = first.iterator();
        final Iterator<T> i2 = second.iterator();
        return new Iterator<T>() {
          @Override
          public boolean hasNext() {
            return i1.hasNext() || i2.hasNext();
          }

          @Override
          public T next() {
            return i1.hasNext()? i1.next() : i2.next();
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  public static <T> Iterable<T> merge(final Collection<Iterable<T>> parts) {
    if (parts.isEmpty()) {
      return Collections.emptyList();
    }
    if (parts.size() == 1) {
      return parts.iterator().next();
    }
    return merge((Iterable<Iterable<T>>)parts);
  }

  public static <T> Iterable<T> merge(final Iterable<Iterable<T>> parts) {
    return new Iterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        final Iterator<Iterable<T>> partsIterator = parts.iterator();
        return new Iterator<T>() {
          Iterator<T> currentPart;
          @Override
          public boolean hasNext() {
            return getCurrentPart() != null;
          }

          @Override
          public T next() {
            final Iterator<T> part = getCurrentPart();
            if (part != null) {
              return part.next();
            }
            throw new NoSuchElementException();
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }

          private Iterator<T> getCurrentPart() {
            while (currentPart == null || !currentPart.hasNext()) {
              if (partsIterator.hasNext()) {
                currentPart = partsIterator.next().iterator();
              }
              else {
                currentPart = null;
                break;
              }
            }
            return currentPart;
          }
        };
      }
    };
  }

  public static <I,O> Iterable<O> convert(final Iterable<? extends I> from, final Function<I, ? extends O> converter) {
    return new Iterable<O>() {
      @NotNull
      @Override
      public Iterator<O> iterator() {
        final Iterator<? extends I> it = from.iterator();
        return new Iterator<O>() {
          @Override
          public boolean hasNext() {
            return it.hasNext();
          }

          @Override
          public O next() {
            return converter.fun(it.next());
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  public static <T> Iterable<T> filter(final Iterable<T> data, final BooleanFunction<? super T> acceptElement) {
    return new Iterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        final Iterator<T> it = data.iterator();
        return new Iterator<T>() {
          private T current = null;
          private boolean isPending = false;

          @Override
          public boolean hasNext() {
            if (!isPending) {
              findNext();
            }
            return isPending;
          }

          @Override
          public T next() {
            try {
              if (!isPending) {
                findNext();
                if (!isPending) {
                  throw new NoSuchElementException();
                }
              }
              return current;
            }
            finally {
              current = null;
              isPending = false;
            }
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }

          private void findNext() {
            isPending = false;
            current = null;
            while (it.hasNext()) {
              final T next = it.next();
              if (acceptElement.fun(next)) {
                isPending = true;

                current = next;
                break;
              }
            }
          }
        };
      }
    };
  }
}
