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

import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFile;
import com.intellij.openapi.vfs.VirtualFile;
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

public class JarHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.jar.JarHandlerBase");

  protected static final long DEFAULT_LENGTH = 0L;
  protected static final long DEFAULT_TIMESTAMP = -1L;

  private final Object myLock = new Object();
  private volatile Reference<Map<String, EntryInfo>> myRelPathsToEntries = new SoftReference<Map<String, EntryInfo>>(null);
  private boolean myCorruptedJar = false;

  protected final String myBasePath;

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
  }

  public File getMirrorFile(@NotNull File originalFile) {
    return originalFile;
  }

  /** @deprecated to be removed in IDEA 15 */
  @SuppressWarnings("deprecation")
  public JarFile getJar() {
    try {
      File mirror = getMirrorFile(getOriginalFile());
      return new MyJarFile(new ZipFile(mirror));
    }
    catch (IOException e) {
      LOG.warn(e.getMessage() + ": " + myBasePath, e);
      return null;
    }
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
            LogUtil.debug(LOG, "mapping %s", myBasePath);
            map = new THashMap<String, EntryInfo>();
            map.put("", new EntryInfo(null, "", true, DEFAULT_LENGTH, DEFAULT_TIMESTAMP));

            try {
              ZipFile zip = getZipFile();
              try {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                  getOrCreate(entries.nextElement(), map, zip);
                }
              }
              finally {
                ZipFileCache.release(zip);
              }
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

  @NotNull
  private ZipFile getZipFile() throws IOException {
    File mirror = getMirrorFile(getOriginalFile());
    return ZipFileCache.acquire(mirror.getPath());
  }

  @NotNull
  private static EntryInfo getOrCreate(ZipEntry entry, Map<String, EntryInfo> map, ZipFile zip) {
    boolean isDirectory = entry.isDirectory();
    String entryName = entry.getName();
    if (StringUtil.endsWithChar(entryName, '/')) {
      entryName = entryName.substring(0, entryName.length() - 1);
      isDirectory = true;
    }

    EntryInfo info = map.get(entryName);
    if (info != null) return info;

    int idx = entryName.lastIndexOf('/');
    String parentName = idx > 0 ? entryName.substring(0, idx) : "";
    String shortName = idx > 0 ? entryName.substring(idx + 1) : entryName;

    EntryInfo parentInfo = getOrCreate(parentName, map, zip);

    if (".".equals(shortName)) {
      return parentInfo;
    }

    info = new EntryInfo(parentInfo, shortName, isDirectory, entry.getSize(), entry.getTime());
    map.put(entryName, info);
    return info;
  }

  @NotNull
  private static EntryInfo getOrCreate(String entryName, Map<String, EntryInfo> map, ZipFile zip) {
    EntryInfo info = map.get(entryName);

    if (info == null) {
      ZipEntry entry = zip.getEntry(entryName + "/");
      if (entry != null) {
        return getOrCreate(entry, map, zip);
      }

      int idx = entryName.lastIndexOf('/');
      String parentName = idx > 0 ? entryName.substring(0, idx) : "";
      String shortName = idx > 0 ? entryName.substring(idx + 1) : entryName;

      EntryInfo parentInfo = getOrCreate(parentName, map, zip);
      info = new EntryInfo(parentInfo, shortName, true, DEFAULT_LENGTH, DEFAULT_TIMESTAMP);
      map.put(entryName, info);
    }

    if (!info.isDirectory) {
      LOG.info(zip.getName() + ": " + entryName + " should be a directory");
      info = new EntryInfo(info.parent, info.shortName, true, info.length, info.timestamp);
      map.put(entryName, info);
    }

    return info;
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
    ZipFile zip = getZipFile();
    try {
      ZipEntry entry = zip.getEntry(getRelativePath(file));
      if (entry != null) {
        InputStream stream = zip.getInputStream(entry);
        if (stream != null) {
          try {
            return FileUtil.loadBytes(stream, (int)entry.getSize());
          }
          finally {
            stream.close();
          }
        }
      }
    }
    finally {
      ZipFileCache.release(zip);
    }
    return ArrayUtil.EMPTY_BYTE_ARRAY;
  }

  @SuppressWarnings("deprecation")
  private static class MyJarFile implements JarFile {
    private static class MyJarEntry implements JarFile.JarEntry {
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
