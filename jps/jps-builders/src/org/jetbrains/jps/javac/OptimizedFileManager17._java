package org.jetbrains.jps.javac;

import com.sun.tools.javac.file.*;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

import javax.lang.model.SourceVersion;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WARNING: Loaded via reflection, do not delete
 *
 * @noinspection UnusedDeclaration
 */
class OptimizedFileManager17 extends com.sun.tools.javac.file.JavacFileManager {
  private boolean myUseZipFileIndex;
  private final Map<File, Archive> myArchives;
  private final Map<File, Boolean> myIsFile = new ConcurrentHashMap<File, Boolean>();

  public OptimizedFileManager17() throws Throwable {
    super(new Context(), true, null);
    final Field archivesField = com.sun.tools.javac.file.JavacFileManager.class.getDeclaredField("archives");
    archivesField.setAccessible(true);
    myArchives = (Map<File, Archive>) archivesField.get(this);
  }

  @Override
  public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
    Iterable<? extends File> path = getLocation(location);
    if (path == null) {
      return List.nil();
    }

    RelativePath.RelativeDirectory subdirectory = new RelativePath.RelativeDirectory(packageName.replace('.', '/'));
    
    ListBuffer<JavaFileObject> results = new ListBuffer<JavaFileObject>();

    for (File file : path) {
      Archive archive = myArchives.get(file);
      
      final boolean isFile;
      if (archive != null) {
        isFile = true;
      }
      else {
        isFile = isFile(file);
      }
      
      if (isFile) {
        // Not a directory; either a file or non-existant, create the archive
        try {
          if (archive == null) {
            archive = openArchive(file);
          }
          listArchive(archive, subdirectory, kinds, recurse, results);
        } 
        catch (IOException ex) {
          log.error("error.reading.file", file, getMessage(ex));
        }
      }
      else {
        listDirectory(file, subdirectory, kinds, recurse, results);
      }
      
    }
    return results.toList();
  }

  private static void listArchive(Archive archive, RelativePath.RelativeDirectory subdirectory, Set<JavaFileObject.Kind> fileKinds, boolean recurse, ListBuffer<JavaFileObject> resultList) {
    // Get the files directly in the subdir
    List<String> files = archive.getFiles(subdirectory);
    if (files != null) {
      for (; !files.isEmpty(); files = files.tail) {
        String file = files.head;
        if (isValidFile(file, fileKinds)) {
          resultList.append(archive.getFileObject(subdirectory, file));
        }
      }
    }
    if (recurse) {
      for (RelativePath.RelativeDirectory s: archive.getSubdirectories()) {
        if (contains(subdirectory, s)) {
          // Because the archive map is a flat list of directories,
          // the enclosing loop will pick up all child subdirectories.
          // Therefore, there is no need to recurse deeper.
          listArchive(archive, s, fileKinds, false, resultList);
        }
      }
    }
  }
  
  private void listDirectory(File directory, RelativePath.RelativeDirectory subdirectory, Set<JavaFileObject.Kind> fileKinds, boolean recurse, ListBuffer<JavaFileObject> resultList) {
    File d = subdirectory.getFile(directory);

    File[] files = d.listFiles();
    if (files == null) {
      return;
    }

    if (sortFiles != null) {
      Arrays.sort(files, sortFiles);
    }

    for (File f: files) {
      String fileName = f.getName();
      if (f.isDirectory()) {
        if (recurse && SourceVersion.isIdentifier(fileName)) {
          listDirectory(directory, new RelativePath.RelativeDirectory(subdirectory, fileName), fileKinds, recurse, resultList);
        }
      } 
      else {
        if (isValidFile(fileName, fileKinds)) {
          //JavaFileObject fe = new RegularFileObject(this, fname, new File(d, fname));
          JavaFileObject fe = getRegularFile(f);
          resultList.append(fe);
        }
      }
    }
  }
  
  private boolean isFile(File root) {
    Boolean cachedIsFile = myIsFile.get(root);
    if (cachedIsFile == null) {
      cachedIsFile = root.isFile();
      myIsFile.put(root, cachedIsFile);
    }
    return cachedIsFile.booleanValue();
  }

  private static boolean contains(RelativePath.RelativeDirectory subdirectory, RelativePath.RelativeDirectory other) {
    final String subdirPath = subdirectory.getPath();
    final String otherPath = other.getPath();
    return otherPath.length() > subdirPath.length() && otherPath.startsWith(subdirPath);
  }

  private static boolean isValidFile(String name, Set<JavaFileObject.Kind> fileKinds) {
    JavaFileObject.Kind kind = getKind(name);
    return fileKinds.contains(kind);
  }

}
