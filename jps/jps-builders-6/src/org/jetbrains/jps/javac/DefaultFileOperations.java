// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class DefaultFileOperations implements FileOperations {
  private static final Iterable<File> NULL_ITERABLE = new Iterable<File>() {
    @NotNull
    @Override
    public Iterator<File> iterator() {
      return Collections.<File>emptyList().iterator();
    }

    @Override
    public String toString() {
      return "NULL_ITERABLE";
    }
  };
  private static final Archive NULL_ARCHIVE = new Archive() {
    @NotNull
    @Override
    public Iterable<JavaFileObject> list(String relPath, Set<JavaFileObject.Kind> kinds, boolean recurse) {
      return Collections.emptyList();
    }
    @Override
    public void close(){
    }

    @Override
    public String toString() {
      return "NULL_ARCHIVE";
    }
  };

  private final Map<File, Iterable<File>> myDirectoryCache = new HashMap<File, Iterable<File>>();
  private final Map<File, FileOperations.Archive> myArchiveCache = new HashMap<File, FileOperations.Archive>();
  private final Map<File, Boolean> myIsFile = new HashMap<File, Boolean>();

  @Override
  @NotNull
  public Iterable<File> listFiles(final File file, final boolean recursively) {
    final Iterable<File> childrenIterable = Iterators.lazy(new Iterators.Provider<Iterable<File>>() {
      @Override
      public Iterable<File> get() {
        final Iterable<File> children = listChildren(file);
        return children == null? Collections.<File>emptyList() : children;
      }
    });
    return !recursively? childrenIterable : Iterators.flat(Iterators.map(childrenIterable, new Function<File, Iterable<File>>() {
      @Override
      public Iterable<File> fun(File ff) {
        return asRecursiveIterable(ff);
      }
    }));
  }

  private Iterable<File> asRecursiveIterable(final File file) {
    return Iterators.flat(Iterators.map(Iterators.asIterable(file), new Function<File, Iterable<File>>() {
      @Override
      public Iterable<File> fun(File f) {
        final Iterable<File> children = listChildren(f);
        if (children == null) { // not a dir
          return Iterators.asIterable(f);
        }
        if (Iterators.isEmptyCollection(children)) {
          return children;
        }
        return Iterators.flat(Iterators.map(children, new Function<File, Iterable<File>>() {
          @Override
          public Iterable<File> fun(File ff) {
            return asRecursiveIterable(ff);
          }
        }));
      }
    }));
  }

  @Override
  public boolean isFile(File file) {
    Boolean cachedIsFile = myIsFile.get(file);
    if (cachedIsFile == null) {
      myIsFile.put(file, cachedIsFile = file.isFile());
    }
    return cachedIsFile.booleanValue();
  }

  @Override
  public void clearCaches(@Nullable File file) {
    if (file != null) {
      myDirectoryCache.clear();
      final Archive arch = myArchiveCache.remove(file);
      if (arch != null) {
        try {
          arch.close();
        }
        catch (IOException ignored) {
        }
      }
    }
    else {
      myDirectoryCache.clear();
      myIsFile.clear();
      final Map<File, FileOperations.Archive> archCache = myArchiveCache;
      for (Map.Entry<File, FileOperations.Archive> entry : archCache.entrySet()) {
        try {
          entry.getValue().close();
        }
        catch (Throwable ignored) {
        }
      }
      archCache.clear();
    }
  }

  @Override
  @Nullable
  public Archive lookupArchive(File file) {
    final Archive arch = myArchiveCache.get(file);
    return arch == NULL_ARCHIVE ? null : arch;
  }

  // returns null if the file is not a supported archive format that can be opened or the file does not exist
  @Override
  public Archive openArchive(File root, final String contentEncoding, @NotNull final JavaFileManager.Location location) throws IOException {
    FileOperations.Archive arch = myArchiveCache.get(root);
    if (arch != null) {
      return arch == NULL_ARCHIVE ? null : arch;
    }
    try {
      arch = new ZipArchive(root, contentEncoding, location);
      myArchiveCache.put(root, arch);
      return arch;
    }
    catch (IOException e) {
      if (isJarOrZip(root)) {
        throw e; // there was really an error reading this archive
      }
      // remember this root as a non-supported file
      myArchiveCache.put(root, NULL_ARCHIVE);
    }
    return null;
  }

  // true iff this file exists, denotes a file and has `jar` or `zip` extension
  private  boolean isJarOrZip(File root) {
    if (!isFile(root)) {
      return false;
    }
    final String name = root.getName();
    return StringUtilRt.endsWithIgnoreCase(name, ".jar") || StringUtilRt.endsWithIgnoreCase(name, ".zip");
  }

  @Nullable
  private Iterable<File> listChildren(File file) {
    Iterable<File> cached = myDirectoryCache.get(file);
    if (cached == null) {
      final Iterable<File> parentFiles = lookupCachedParent(file);
      if (parentFiles == null || (parentFiles != NULL_ITERABLE && !Iterators.isEmptyCollection(parentFiles))) {
        try {
          cached = listFiles(file);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      myDirectoryCache.put(file, cached == null? NULL_ITERABLE : cached);
    }
    return cached == NULL_ITERABLE? null : cached;
  }

  private Iterable<File> lookupCachedParent(File file) {
    if (!myDirectoryCache.isEmpty()) {
      File parent = file.getParentFile();
      for (int i = 0; i < 10 && parent != null; i++) { // at most 10 levels upwards
        final Iterable<File> cached = myDirectoryCache.get(parent);
        if (cached != null) {
          return cached;
        }
        parent = parent.getParentFile();
      }
    }
    return null;
  }

  @Nullable
  private static Iterable<File> listFiles(File file) throws IOException {
    final File[] files = file.listFiles();
    return files != null? Arrays.asList(files) : null;
  }

  private static class ZipArchive implements FileOperations.Archive {
    private final ZipFile myZip;
    private final Map<String, Collection<ZipEntry>> myPaths = new HashMap<String, Collection<ZipEntry>>();
    private final Function<ZipEntry, JavaFileObject> myToFileObjectConverter;
    private static final FileObjectKindFilter<ZipEntry> ourEntryFilter = new FileObjectKindFilter<ZipEntry>(new Function<ZipEntry, String>() {
      @Override
      public String fun(ZipEntry zipEntry) {
        return zipEntry.getName();
      }
    });

    ZipArchive(final File root, final String encodingName, final JavaFileManager.Location location) throws IOException {
      myZip = new ZipFile(root, ZipFile.OPEN_READ);
      final Enumeration<? extends ZipEntry> entries = myZip.entries();
      while (entries.hasMoreElements()) {
        final ZipEntry entry = entries.nextElement();
        if (!entry.isDirectory()) {
          final String parent = getParentPath(entry.getName());
          Collection<ZipEntry> children = myPaths.get(parent);
          if (children == null) {
            children = new ArrayList<ZipEntry>();
            myPaths.put(parent, children);
          }
          children.add(entry);
        }
      }
      myToFileObjectConverter = new Function<ZipEntry, JavaFileObject>() {
        @Override
        public JavaFileObject fun(ZipEntry zipEntry) {
          return new ZipFileObject(root, myZip, zipEntry, encodingName, location);
        }
      };
    }

    @NotNull
    @Override
    public Iterable<JavaFileObject> list(final String relPath, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException{
      final Collection<ZipEntry> entries = myPaths.get(relPath);
      if (entries == null || entries.isEmpty()) {
        return Collections.emptyList();
      }
      Iterable<ZipEntry> entriesIterable = entries;
      if (recurse) {
        if (relPath.isEmpty()) {
          entriesIterable = Iterators.flat(myPaths.values());
        }
        else {
          final Iterable<Map.Entry<String, Collection<ZipEntry>>> baseIterable = Iterators.filter(myPaths.entrySet(), new BooleanFunction<Map.Entry<String, Collection<ZipEntry>>>() {
            @Override
            public boolean fun(Map.Entry<String, Collection<ZipEntry>> e) {
              final String dir = e.getKey();
              return dir.startsWith(relPath) && (dir.length() == relPath.length() || dir.charAt(relPath.length()) == '/');
            }
          });
          entriesIterable = Iterators.flat(Iterators.map(baseIterable, new Function<Map.Entry<String, Collection<ZipEntry>>, Iterable<ZipEntry>>() {
            @Override
            public Iterable<ZipEntry> fun(Map.Entry<String, Collection<ZipEntry>> e) {
              return e.getValue();
            }
          }));
        }
      }
      return Iterators.map(Iterators.filter(entriesIterable, ourEntryFilter.getFor(kinds)), myToFileObjectConverter);
    }

    @Override
    public void close() throws IOException {
      myPaths.clear();
      myZip.close();
    }

    private static String getParentPath(String path) {
      int idx = path.lastIndexOf('/');
      if (idx == path.length() - 1) { // the very last char
        idx = path.lastIndexOf('/', idx - 1);
      }
      if (idx < 0) {
        return "";
      }
      return path.substring(0, idx);
    }
  }
}
