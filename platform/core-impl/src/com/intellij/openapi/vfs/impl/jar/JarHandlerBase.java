/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.ZipHandler;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** @deprecated to be removed in IDEA 15 */
@SuppressWarnings({"deprecation", "UnnecessaryFullyQualifiedName"})
public class JarHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.jar.JarHandlerBase");

  protected static final long DEFAULT_LENGTH = 0L;
  protected static final long DEFAULT_TIMESTAMP = -1L;

  private final Object myLock = new Object();
  private volatile Reference<Map<String, EntryInfo>> myRelPathsToEntries = new SoftReference<Map<String, EntryInfo>>(null);
  private boolean myCorruptedJar = false;

  protected final String myBasePath;

  private final MyZipHandler myHandler;

  private static class MyZipHandler extends ZipHandler {
    public MyZipHandler(@NotNull String path) {
      super(path);
    }

    @NotNull
    public Map<String, JarHandlerBase.EntryInfo> getEntries() throws IOException {
      Map<String, EntryInfo> src = createEntriesMap();
      Map<String, JarHandlerBase.EntryInfo> map = new THashMap<String, JarHandlerBase.EntryInfo>();
      map.put("", new JarHandlerBase.EntryInfo(null, "", true, DEFAULT_LENGTH, DEFAULT_TIMESTAMP));
      for (String path : src.keySet()) {
        map.put(path, getOrCreate(map, path, src.get(path)));
      }
      return map;
    }

    private static JarHandlerBase.EntryInfo getOrCreate(Map<String, JarHandlerBase.EntryInfo> map, String path, EntryInfo e) {
      JarHandlerBase.EntryInfo entry = map.get(path);
      if (entry == null) {
        int p = path.lastIndexOf('/');
        String parentPath = p > 0 ? path.substring(0, p) : "";
        entry = new JarHandlerBase.EntryInfo(getOrCreate(map, parentPath, e.parent), e.shortName, e.isDirectory, e.length, e.timestamp);
        map.put(path, entry);
      }
      return entry;
    }
  }

  protected static class EntryInfo {
    protected final EntryInfo parent;
    protected final String shortName;
    protected final boolean isDirectory;
    protected final long length;
    protected final long timestamp;

    public EntryInfo(EntryInfo parent, @NotNull String shortName, boolean isDirectory, long length, long timestamp) {
      this.parent = parent;
      this.shortName = shortName;
      this.isDirectory = isDirectory;
      this.length = length;
      this.timestamp = timestamp;
    }
  }

  public JarHandlerBase(@NotNull String path) {
    myBasePath = path;
    myHandler = new MyZipHandler(path);
  }

  public File getMirrorFile(@NotNull File originalFile) {
    return originalFile;
  }

  @NotNull
  protected File getOriginalFile() {
    return new File(myBasePath);
  }

  @NotNull
  public String[] list(@NotNull VirtualFile file) {
    EntryInfo parentEntry = getEntryInfo(file);

    Set<String> names = new HashSet<String>();
    for (EntryInfo info : getEntriesMap().values()) {
      if (info.parent == parentEntry) {
        names.add(info.shortName);
      }
    }

    return ArrayUtil.toStringArray(names);
  }

  protected EntryInfo getEntryInfo(@NotNull VirtualFile file) {
    return getEntryInfo(getRelativePath(file));
  }

  private String getRelativePath(VirtualFile file) {
    String path = file.getPath().substring(myBasePath.length() + 1);
    return StringUtil.startsWithChar(path, '/') ? path.substring(1) : path;
  }

  public EntryInfo getEntryInfo(@NotNull String parentPath) {
    return getEntriesMap().get(parentPath);
  }

  @NotNull
  protected Map<String, EntryInfo> getEntriesMap() {
    Map<String, EntryInfo> map = SoftReference.dereference(myRelPathsToEntries);
    if (map == null) {
      synchronized (myLock) {
        map = SoftReference.dereference(myRelPathsToEntries);
        if (map == null) {
          if (myCorruptedJar) {
            map = Collections.emptyMap();
          }
          else {
            try {
              map = myHandler.getEntries();
            }
            catch (IOException e) {
              myCorruptedJar = true;
              LOG.warn(e.getMessage() + ": " + myBasePath, e);
              map = Collections.emptyMap();
            }
          }

          myRelPathsToEntries = new SoftReference<Map<String, EntryInfo>>(Collections.unmodifiableMap(map));
        }
      }
    }
    return map;
  }

  public long getLength(@NotNull VirtualFile file) {
    if (file.getParent() == null) return getOriginalFile().length();
    EntryInfo entry = getEntryInfo(file);
    return entry == null ? DEFAULT_LENGTH : entry.length;
  }

  public long getTimeStamp(@NotNull VirtualFile file) {
    if (file.getParent() == null) return getOriginalFile().lastModified();
    EntryInfo entry = getEntryInfo(file);
    return entry == null ? DEFAULT_TIMESTAMP : entry.timestamp;
  }

  public boolean isDirectory(@NotNull VirtualFile file) {
    if (file.getParent() == null) return true;
    EntryInfo info = getEntryInfo(file);
    return info == null || info.isDirectory;
  }

  public boolean exists(@NotNull VirtualFile file) {
    if (file.getParent() == null) return getOriginalFile().exists();
    EntryInfo info = getEntryInfo(file);
    return info != null;
  }

  @Nullable
  public FileAttributes getAttributes(@NotNull VirtualFile file) {
    if (file.getParent() == null) {
      FileAttributes attributes = FileSystemUtil.getAttributes(getOriginalFile());
      return attributes == null ? null : new FileAttributes(true, false, false, false, attributes.length, attributes.lastModified, false);
    }

    EntryInfo entry = getEntryInfo(file);
    return entry == null ? null : new FileAttributes(entry.isDirectory, false, false, false, entry.length, entry.timestamp, false);
  }

  @NotNull
  public InputStream getInputStream(@NotNull VirtualFile file) throws IOException {
    return new BufferExposingByteArrayInputStream(contentsToByteArray(file));
  }

  @NotNull
  public byte[] contentsToByteArray(@NotNull VirtualFile file) throws IOException {
    return myHandler.contentsToByteArray(getRelativePath(file));
  }

  /** @deprecated to be removed in IDEA 15 */
  public com.intellij.openapi.vfs.JarFile getJar() {
    try {
      File mirror = getMirrorFile(getOriginalFile());
      return new MyJarFile(new ZipFile(mirror));
    }
    catch (IOException e) {
      LOG.warn(e.getMessage() + ": " + myBasePath, e);
      return null;
    }
  }

  static class MyJarFile implements com.intellij.openapi.vfs.JarFile {
    private static class MyJarEntry implements com.intellij.openapi.vfs.JarFile.JarEntry {
      private final ZipEntry myEntry;

      MyJarEntry(ZipEntry entry) {
        myEntry = entry;
      }

      @Override
      public String getName() {
        return myEntry.getName();
      }

      @Override
      public long getSize() {
        return myEntry.getSize();
      }

      @Override
      public long getTime() {
        return myEntry.getTime();
      }

      @Override
      public boolean isDirectory() {
        return myEntry.isDirectory();
      }
    }

    private final ZipFile myZipFile;

    public MyJarFile(ZipFile zipFile) {
      myZipFile = zipFile;
    }

    @Override
    public JarEntry getEntry(String name) {
      try {
        ZipEntry entry = myZipFile.getEntry(name);
        if (entry != null) {
          return new MyJarEntry(entry);
        }
      }
      catch (RuntimeException e) {
        LOG.warn("corrupted: " + myZipFile.getName(), e);
      }
      return null;
    }

    @Override
    public InputStream getInputStream(JarEntry entry) throws IOException {
      return myZipFile.getInputStream(((MyJarEntry)entry).myEntry);
    }

    @Override
    public Enumeration<? extends JarEntry> entries() {
      return new Enumeration<JarEntry>() {
        private final Enumeration<? extends ZipEntry> entries = myZipFile.entries();

        @Override
        public boolean hasMoreElements() {
          return entries.hasMoreElements();
        }

        @Override
        public JarEntry nextElement() {
          try {
            ZipEntry entry = entries.nextElement();
            if (entry != null) {
              return new MyJarEntry(entry);
            }
          }
          catch (RuntimeException e) {
            LOG.warn("corrupted: " + myZipFile.getName(), e);
          }
          return null;
        }
      };
    }

    @Override
    public ZipFile getZipFile() {
      return myZipFile;
    }
  }
}
