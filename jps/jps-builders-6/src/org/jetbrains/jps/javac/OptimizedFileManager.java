/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

import javax.lang.model.SourceVersion;
import javax.tools.*;
import java.io.*;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WARNING: Loaded via reflection, do not delete
 *
 * @author nik
 * @noinspection UnusedDeclaration
 */
class OptimizedFileManager extends DefaultFileManager {
  private boolean myUseZipFileIndex;
  private final Map<File, Archive> myArchives;
  private final Map<File, Boolean> myIsFile = new HashMap<File, Boolean>();
  private final Map<File, File[]> myDirectoryCache = new HashMap<File, File[]>();
  public static final File[] NULL_FILE_ARRAY = new File[0];

  private static final boolean ourUseContentCache = Boolean.valueOf(System.getProperty("javac.use.content.cache", "false"));
  private final Map<InputFileObject, SoftReference<CharBuffer>> myContentCache = ourUseContentCache? new HashMap<InputFileObject, SoftReference<CharBuffer>>() : Collections.<InputFileObject, SoftReference<CharBuffer>>emptyMap();

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
  public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
    final String name = packageName == null || packageName.isEmpty() ? relativeName.replace('\\', '/') : (packageName.replace('.', '/') + "/" + relativeName.replace('\\', '/'));
    return getFileForInput(location, name);
  }

  @Override
  public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) throws IOException {
    final String name = className.replace('.', '/') + kind.extension;
    return getFileForInput(location, name);
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
      result.add(new InputFileObject(f));
    }
    return result;
  }

  @Override
  public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
    Iterable<? extends File> locationRoots = getLocation(location);
    if (locationRoots == null) {
      return Collections.emptyList();
    }

    final String relativePath = packageName.replace('.', File.separatorChar);
    ListBuffer<JavaFileObject> results = new ListBuffer<JavaFileObject>();

    for (File root : locationRoots) {
      final Archive archive = myArchives.get(root);
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
        final File directory = relativePath.length() != 0 ? new File(root, relativePath) : root;
        if (recurse) {
          collectFromDirectoryRecursively(directory, kinds, results, true);
        }
        else {
          collectFromDirectory(directory, kinds, results);
        }
      }
    }

    return results.toList();
  }

  // important! called via reflection, so avoid renaming or signature changing or rename carefully
  public void fileGenerated(File file) {
    final File parent = file.getParentFile();
    if (parent != null) {
      myDirectoryCache.remove(parent);
    }
  }

  private boolean isFile(File root) {
    Boolean cachedIsFile = myIsFile.get(root);
    if (cachedIsFile == null) {
      cachedIsFile = Boolean.valueOf(root.isFile());
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

  private void collectFromDirectory(File directory, Set<JavaFileObject.Kind> fileKinds, ListBuffer<JavaFileObject> result) {
    final File[] children = listChildren(directory);
    if (children != null) {
      final boolean acceptUnknownFiles = fileKinds.contains(JavaFileObject.Kind.OTHER);
      for (File child : children) {
        if (isValidFile(child.getName(), fileKinds)) {
          if (acceptUnknownFiles && !isFile(child)) {
            continue;
          }
          final JavaFileObject fe = new InputFileObject(child);
          result.append(fe);
        }
      }
    }
  }

  private void collectFromDirectoryRecursively(File file, Set<JavaFileObject.Kind> fileKinds, ListBuffer<JavaFileObject> result, boolean isRootCall) {
    final File[] children = listChildren(file);
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
        JavaFileObject fe = new InputFileObject(file);
        result.append(fe);
      }
    }
  }

  private File[] listChildren(File file) {
    File[] cached = myDirectoryCache.get(file);
    if (cached == null) {
      cached = file.listFiles();
      myDirectoryCache.put(file, cached != null? cached : NULL_FILE_ARRAY);
    }
    return cached == NULL_FILE_ARRAY ? null : cached;
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

  private JavaFileObject getFileForInput(Location location, String name) throws IOException {
    Iterable<? extends File> path = getLocation(location);
    if (path == null) {
      return null;
    }

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
        if (archive == null) {
          try {
            archive = openArchive(root);
          }
          catch (IOException ex) {
            log.error("error.reading.file", root, ex.getLocalizedMessage());
            break;
          }
        }
        if (archive.contains(name)) {
          int i = name.lastIndexOf('/');
          String dirname = name.substring(0, i+1);
          String basename = name.substring(i+1);
          return archive.getFileObject(dirname, basename);
        }
      }
      else {
        final File f = new File(root, name.replace('/', File.separatorChar));
        if (f.exists()) {
          return new InputFileObject(f);
        }
      }
    }
    return null;
  }

  //actually Javac doesn't check if this method returns null. It always get substring of the returned string starting from the last dot.
  @Override
  public String inferBinaryName(Location location, JavaFileObject file) {
    final String name = file.getName();
    int dot = name.lastIndexOf('.');
    final String relativePath = dot != -1 ? name.substring(0, dot) : name;
    return relativePath.replace(File.separatorChar, '.');
  }

  private class InputFileObject extends BaseFileObject {

    /** The underlying file.
     */
    final File f;

    public InputFileObject(File f) {
      this.f = f;
    }

    public InputStream openInputStream() throws IOException {
      return new FileInputStream(f);
    }

    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
      throw new UnsupportedOperationException();
    }

    public OutputStream openOutputStream() throws IOException {
      throw new UnsupportedOperationException();
    }

    public Writer openWriter() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Deprecated
    public String getName() {
      return f.getPath();
    }

    public boolean isNameCompatible(String simpleName, JavaFileObject.Kind kind) {
      final String n = simpleName + kind.extension;
      final String fileName = f.getName();
      if (fileName.equals(n)) {
        return true;
      }
      if (fileName.equalsIgnoreCase(n)) {
        try {
          // allow for Windows
          return (f.getCanonicalFile().getName().equals(n));
        }
        catch (IOException e) {
        }
      }
      return false;
    }

    /** @deprecated see bug 6410637 */
    @Deprecated
    public String getPath() {
      return f.getPath();
    }

    public long getLastModified() {
      return f.lastModified();
    }

    public boolean delete() {
      return f.delete();
    }

    public CharBuffer getCharContent(boolean ignoreEncodingErrors) throws IOException {
      CharBuffer cb;
      if (ourUseContentCache) {
        SoftReference<CharBuffer> ref = myContentCache.get(this);
        cb = (ref != null) ? ref.get() : null;
        if (cb == null) {
          cb = loadFileContent(ignoreEncodingErrors);
          if (!ignoreEncodingErrors) {
            myContentCache.put(this, new SoftReference<CharBuffer>(cb));
          }
        }
      }
      else {
        cb = loadFileContent(ignoreEncodingErrors);
      }
      return cb;
    }

    private CharBuffer loadFileContent(boolean ignoreEncodingErrors) throws IOException {
      final InputStream in = new FileInputStream(f);
      final ByteBuffer bb = makeByteBuffer(in);
      JavaFileObject prev = log.useSource(this);
      try {
        return decode(bb, ignoreEncodingErrors);
      }
      finally {
        log.useSource(prev);
        myByteBufferCache.put(bb); // save for next time
        in.close();
      }
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof InputFileObject)) {
        return false;
      }
      InputFileObject o = (InputFileObject) other;
      try {
        return f.equals(o.f) || f.getCanonicalFile().equals(o.f.getCanonicalFile());
      }
      catch (IOException e) {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return f.hashCode();
    }

    public URI toUri() {
      try {
        return convertToURI(f.getPath());
      }
      catch (Throwable ex) {
        return f.toURI().normalize();
      }
    }
  }

  private static URI convertToURI(String localPath) throws URISyntaxException {
    String p = localPath.replace('\\', '/');
    if (!p.startsWith("/")) {
      p = "/" + p;
    }
    if (!p.startsWith("//")) {
      p = "//" + p;
    }
    return new URI("file", null, p, null);
  }

  private ByteBuffer makeByteBuffer(InputStream in) throws IOException {
    int limit = in.available();
    if (limit < 1024) {
      limit = 1024;
    }
    ByteBuffer result = myByteBufferCache.get(limit);
    int position = 0;
    while (in.available() != 0) {
      if (position >= limit) {
        // expand buffer
        result = ByteBuffer.allocate(limit <<= 1).put((ByteBuffer)result.flip());
      }
      final int count = in.read(result.array(), position, limit - position);
      if (count < 0) {
        break;
      }
      result.position(position += count);
    }
    return (ByteBuffer)result.flip();
  }

  private CharBuffer decode(ByteBuffer inbuf, boolean ignoreEncodingErrors) {
    CharsetDecoder decoder;
    String encodingName = getEncodingName();
    try {
      Charset charset = (this.charset == null) ? Charset.forName(encodingName) : this.charset;
      decoder = charset.newDecoder();

      CodingErrorAction action;
      if (ignoreEncodingErrors) {
        action = CodingErrorAction.REPLACE;
      }
      else {
        action = CodingErrorAction.REPORT;
      }

      decoder.onMalformedInput(action).onUnmappableCharacter(action);
    }
    catch (IllegalCharsetNameException e) {
      log.error("unsupported.encoding", encodingName);
      return (CharBuffer)CharBuffer.allocate(1).flip();
    }
    catch (UnsupportedCharsetException e) {
      log.error("unsupported.encoding", encodingName);
      return (CharBuffer)CharBuffer.allocate(1).flip();
    }

    // slightly overestimate the buffer size to avoid reallocation.
    final float factor = decoder.averageCharsPerByte() * 0.8f + decoder.maxCharsPerByte() * 0.2f;
    CharBuffer dest = CharBuffer.allocate(10 + (int)(inbuf.remaining() * factor));

    while (true) {
      CoderResult result = decoder.decode(inbuf, dest, true);
      dest.flip();

      if (result.isUnderflow()) { // done reading
        // make sure there is at least one extra character
        if (dest.limit() == dest.capacity()) {
          dest = CharBuffer.allocate(dest.capacity()+1).put(dest);
          dest.flip();
        }
        return dest;
      }
      else if (result.isOverflow()) { // buffer too small; expand
        int newCapacity = 10 + dest.capacity() + (int)(inbuf.remaining()*decoder.maxCharsPerByte());
        dest = CharBuffer.allocate(newCapacity).put(dest);
      }
      else if (result.isMalformed() || result.isUnmappable()) {
        // bad character in input

        // report coding error (warn only pre 1.5)
        if (!getSource().allowEncodingErrors()) {
          log.error(new JCDiagnostic.SimpleDiagnosticPosition(dest.limit()), "illegal.char.for.encoding", charset == null ? encodingName : charset.name());
        }
        else {
          log.warning(new JCDiagnostic.SimpleDiagnosticPosition(dest.limit()), "illegal.char.for.encoding", charset == null ? encodingName : charset.name());
        }

        // skip past the coding error
        inbuf.position(inbuf.position() + result.length());

        // undo the flip() to prepare the output buffer
        // for more translation
        dest.position(dest.limit());
        dest.limit(dest.capacity());
        dest.put((char)0xfffd); // backward compatible
      }
      else {
        throw new AssertionError(result);
      }
    }
    // unreached
  }

  private static class ByteBufferCache {
    private final AtomicReference<ByteBuffer> myCached = new AtomicReference<ByteBuffer>(null);

    ByteBuffer get(int capacity) {
      if (capacity < 20480) {
        capacity = 20480;
      }
      final ByteBuffer cached = myCached.getAndSet(null);
      return (cached != null && cached.capacity() >= capacity) ? (ByteBuffer)cached.clear() : ByteBuffer.allocate(capacity + capacity>>1);
    }

    void put(ByteBuffer x) {
      myCached.set(x);
    }

    void clear() {
      myCached.set(null);
    }
  }
  private final ByteBufferCache myByteBufferCache = new ByteBufferCache();


  private static volatile boolean ourPathCacheClearProblem = false;

  public void close() {
    try {
      super.close();
    }
    finally {
      // archives are cleared in super.close()
      if (ourUseContentCache) {
        myContentCache.clear();
      }
      myDirectoryCache.clear();
      myByteBufferCache.clear();
      myIsFile.clear();
      if (!ourPathCacheClearProblem) {
        try {
          Paths.clearPathExistanceCache();
        }
        catch (Throwable ignored) {
          ourPathCacheClearProblem = true;
        }
      }
    }
  }
}
