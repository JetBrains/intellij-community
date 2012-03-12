package org.jetbrains.jps.javac;

import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DefaultFileManager;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

import javax.lang.model.SourceVersion;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WARNING: Loaded via reflection, do not delete
 *
 * @author nik
 * @noinspection UnusedDeclaration
 */
class OptimizedFileManager extends DefaultFileManager {
  private boolean myUseZipFileIndex;
  private final Map<File, Archive> myArchives;
  private final Map<File, Boolean> myIsFile = new ConcurrentHashMap<File, Boolean>();

  public OptimizedFileManager() throws Throwable {
    super(new Context(), true, null);
    final Field archivesField = DefaultFileManager.class.getDeclaredField("archives");
    archivesField.setAccessible(true);
    myArchives = (Map<File, Archive>) archivesField.get(this);

    try {
      final Field useZipFileIndexField = DefaultFileManager.class.getDeclaredField("useZipFileIndex");
      useZipFileIndexField.setAccessible(true);
      myUseZipFileIndex = (Boolean) useZipFileIndexField.get(this);
    }
    catch (Exception e) {
      myUseZipFileIndex = false;
    }
  }

  @Override
  public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
    Iterable<? extends File> path = getLocation(location);
    if (path == null) return Collections.emptyList();

    String relativePath = packageName.replace('.', File.separatorChar);
    ListBuffer<JavaFileObject> results = new ListBuffer<JavaFileObject>();

    for (File root : path) {
      Archive archive = myArchives.get(root);
      final boolean isFile;
      if (archive != null) {
        isFile = true;
      }
      else {
        isFile = isFile(root);
      }
      if (isFile) {
        collectFromArchive(root, archive, relativePath, kinds, recurse, results);
      }
      else {
        File directory = relativePath.length() != 0 ? new File(root, relativePath) : root;
        if (recurse) {
          collectFromDirectoryRecursively(directory, kinds, results, true);
        }
        else {
          collectFromDirectory(directory, kinds, false, results);
        }
      }
    }

    return results.toList();
  }

  private boolean isFile(File root) {
    Boolean cachedIsFile = myIsFile.get(root);
    if (cachedIsFile == null) {
      cachedIsFile = root.isFile();
      myIsFile.put(root, cachedIsFile);
    }
    return cachedIsFile.booleanValue();
  }

  private void collectFromArchive(File root, Archive archive, String relativePath, Set<JavaFileObject.Kind> kinds, boolean recurse, ListBuffer<JavaFileObject> result) {
    if (archive == null) {
      try {
        archive = openArchive(root);
      }
      catch (IOException ex) {
        log.error("error.reading.file", root, ex.getLocalizedMessage());
        return;
      }
    }
    final String separator = myUseZipFileIndex ? File.separator : "/";
    if (relativePath.length() != 0) {
      if (!myUseZipFileIndex) {
        relativePath = relativePath.replace('\\', '/');
      }
      if (!relativePath.endsWith(separator)) {
        relativePath = relativePath + separator;
      }
    }

    collectArchiveFiles(archive, relativePath, kinds, result);
    if (recurse) {
      for (String s : archive.getSubdirectories()) {
        if (s.startsWith(relativePath) && !s.equals(relativePath)) {
          if (!s.endsWith(separator)) {
            s += separator;
          }
          collectArchiveFiles(archive, s, kinds, result);
        }
      }
    }
  }

  private void collectFromDirectory(File directory, Set<JavaFileObject.Kind> fileKinds, boolean recurse, ListBuffer<JavaFileObject> result) {
    final File[] children = directory.listFiles();
    if (children != null) {
      for (File child : children) {
        if (isValidFile(child.getName(), fileKinds) && isFile(child)) {
          final JavaFileObject fe = getRegularFile(child);
          result.append(fe);
        }
      }
    }
  }

  private void collectFromDirectoryRecursively(File file, Set<JavaFileObject.Kind> fileKinds, ListBuffer<JavaFileObject> result, boolean isRootCall) {
    final File[] children = file.listFiles();
    final String name = file.getName();
    if (children != null) { // is directory
      if (isRootCall || SourceVersion.isIdentifier(name)) {
        for (File child : children) {
          collectFromDirectoryRecursively(child, fileKinds, result, false);
        }
      }
    }
    else {
      if (isValidFile(name, fileKinds)) {
        JavaFileObject fe = getRegularFile(file);
        result.append(fe);
      }
    }
  }

  private void collectArchiveFiles(Archive archive, String relativePath, Set<JavaFileObject.Kind> fileKinds, ListBuffer<JavaFileObject> result) {
    List<String> files = archive.getFiles(relativePath);
    if (files != null) {
      for (String file; !files.isEmpty(); files = files.tail) {
        file = files.head;
        if (isValidFile(file, fileKinds)) {
          result.append(archive.getFileObject(relativePath, file));
        }
      }
    }
  }

  private boolean isValidFile(String name, Set<JavaFileObject.Kind> fileKinds) {
    int dot = name.lastIndexOf(".");
    JavaFileObject.Kind kind = getKind(dot == -1 ? name : name.substring(dot));
    return fileKinds.contains(kind);
  }

  //actually Javac doesn't check if this method returns null. It always get substring of the returned string starting from the last dot.
  @Override
  public String inferBinaryName(Location location, JavaFileObject file) {
      final String name = file.getName();
      int dot = name.lastIndexOf('.');
      final String relativePath = dot != -1 ? name.substring(0, dot) : name;
      return relativePath.replace(File.separatorChar, '.');
  }
}
