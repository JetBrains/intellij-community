package org.jetbrains.jps.javac;

import com.sun.tools.javac.file.BaseFileObject;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.file.RelativePath;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

import javax.lang.model.SourceVersion;
import javax.tools.*;
import java.io.*;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.util.*;
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
  public Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(Iterable<? extends File> files) {
    java.util.List<InputFileObject> result;
    if (files instanceof Collection) {
      result = new ArrayList<InputFileObject>(((Collection)files).size());
    }
    else {
      result = new ArrayList<InputFileObject>();
    }
    for (File f: files) {
      result.add(new InputFileObject(this, f));
    }
    return result;
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
        final File dir = subdirectory.getFile(file);
        if (recurse) {
          listDirectoryRecursively(dir, kinds, results, true);
        }
        else {
          listDirectory(dir, kinds, results);
        }
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
  
  private void listDirectory(File directory, Set<JavaFileObject.Kind> fileKinds, ListBuffer<JavaFileObject> resultList) {
    final File[] files = directory.listFiles();
    if (files != null) {
      if (sortFiles != null) {
        Arrays.sort(files, sortFiles);
      }
  
      for (File f: files) {
        String fileName = f.getName();
        if (isValidFile(fileName, fileKinds) && isFile(f)) {
          JavaFileObject fe = new InputFileObject(this, f);
          resultList.append(fe);
        }
      }
    }
  }

  private void listDirectoryRecursively(File file, Set<JavaFileObject.Kind> fileKinds, ListBuffer<JavaFileObject> resultList, boolean isRootCall) {
    final File[] children = file.listFiles();
    final String fileName = file.getName();
    if (children != null) { // is directory
      if (isRootCall || SourceVersion.isIdentifier(fileName)) {
        if (sortFiles != null) {
          Arrays.sort(children, sortFiles);
        }
        for (File child : children) {
          listDirectoryRecursively(child, fileKinds, resultList, false);
        }
      }
    }
    else {
      if (isValidFile(fileName, fileKinds)) {
        JavaFileObject fe = new InputFileObject(this, file);
        resultList.append(fe);
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
    return fileKinds.contains(getKind(name));
  }

  private class InputFileObject extends BaseFileObject {
    private String name;
    final File file;
    private Reference<File> absFileRef;

    public InputFileObject(JavacFileManager fileManager, File f) {
      this(fileManager, f.getName(), f);
    }

    public InputFileObject(JavacFileManager fileManager, String name, File f) {
      super(fileManager);
      this.name = name;
      this.file = f;
    }


    @Override
    public URI toUri() {
      return file.toURI().normalize();
    }

    @Override
    public String getName() {
      return file.getPath();
    }

    @Override
    public String getShortName() {
      return name;
    }

    @Override
    public JavaFileObject.Kind getKind() {
      return getKind(name);
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public Writer openWriter() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getLastModified() {
      return file.lastModified();
    }

    @Override
    public boolean delete() {
      return file.delete();
    }

    @Override
    protected CharsetDecoder getDecoder(boolean ignoreEncodingErrors) {
      return fileManager.getDecoder(fileManager.getEncodingName(), ignoreEncodingErrors);
    }

    @Override
    protected String inferBinaryName(Iterable<? extends File> path) {
      String fPath = file.getPath();
      //System.err.println("RegularFileObject " + file + " " +r.getPath());
      for (File dir: path) {
        //System.err.println("dir: " + dir);
        String dPath = dir.getPath();
        if (dPath.length() == 0)
          dPath = System.getProperty("user.dir");
        if (!dPath.endsWith(File.separator))
          dPath += File.separator;
        if (fPath.regionMatches(true, 0, dPath, 0, dPath.length())
            && new File(fPath.substring(0, dPath.length())).equals(new File(dPath))) {
          String relativeName = fPath.substring(dPath.length());
          return removeExtension(relativeName).replace(File.separatorChar, '.');
        }
      }
      return null;
    }

    @Override
    public boolean isNameCompatible(String cn, JavaFileObject.Kind kind) {
      cn.getClass();
      // null check
      if (kind == Kind.OTHER && getKind() != kind) {
        return false;
      }
      String n = cn + kind.extension;
      if (name.equals(n)) {
        return true;
      }
      if (name.equalsIgnoreCase(n)) {
        return file.getName().equals(n);
      }
      return false;
    }

    /**
     * Check if two file objects are equal.
     * Two RegularFileObjects are equal if the absolute paths of the underlying
     * files are equal.
     */
    @Override
    public boolean equals(Object other) {
      if (this == other)
        return true;

      if (!(other instanceof InputFileObject))
        return false;

      InputFileObject o = (InputFileObject) other;
      return getAbsoluteFile().equals(o.getAbsoluteFile());
    }

    @Override
    public int hashCode() {
      return getAbsoluteFile().hashCode();
    }

    private File getAbsoluteFile() {
      File absFile = (absFileRef == null ? null : absFileRef.get());
      if (absFile == null) {
        absFile = file.getAbsoluteFile();
        absFileRef = new SoftReference<File>(absFile);
      }
      return absFile;
    }

    public CharBuffer getCharContent(boolean ignoreEncodingErrors) throws IOException {
      CharBuffer cb = fileManager.getCachedContent(this);
      if (cb == null) {
        InputStream in = new FileInputStream(file);
        try {
          ByteBuffer bb = fileManager.makeByteBuffer(in);
          JavaFileObject prev = fileManager.log.useSource(this);
          try {
            cb = fileManager.decode(bb, ignoreEncodingErrors);
          } finally {
            fileManager.log.useSource(prev);
          }
          fileManager.recycleByteBuffer(bb);
          if (!ignoreEncodingErrors) {
            fileManager.cache(this, cb);
          }
        } finally {
          in.close();
        }
      }
      return cb;
    }
  }


}
