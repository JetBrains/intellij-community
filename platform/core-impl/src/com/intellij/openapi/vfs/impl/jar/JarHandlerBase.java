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
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.TimedReference;
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

  private static final long DEFAULT_LENGTH = 0L;
  private static final long DEFAULT_TIMESTAMP = -1L;

  private final TimedReference<JarFile> myJarFile = new TimedReference<JarFile>(null);
  private volatile Reference<Map<String, EntryInfo>> myRelPathsToEntries = new SoftReference<Map<String, EntryInfo>>(null);
  private final Object lock = new Object();

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

  protected void clear() {
    synchronized (lock) {
      myRelPathsToEntries = null;
      myJarFile.set(null);
    }
  }

  public File getMirrorFile(@NotNull File originalFile) {
    return originalFile;
  }

  @Nullable
  public JarFile getJar() {
    JarFile jar = myJarFile.get();
    if (jar == null) {
      synchronized (lock) {
        jar = myJarFile.get();
        if (jar == null) {
          jar = createJarFile();
          if (jar != null) {
            myJarFile.set(jar);
          }
        }
      }
    }
    return jar;
  }

  @Nullable
  protected JarFile createJarFile() {
    final File originalFile = getOriginalFile();
    try {
      @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
      final ZipFile zipFile = new ZipFile(getMirrorFile(originalFile));

      class MyJarEntry implements JarFile.JarEntry {
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

      return new JarFile() {
        @Override
        public JarFile.JarEntry getEntry(String name) {
          try {
            ZipEntry entry = zipFile.getEntry(name);
            if (entry != null) {
              return new MyJarEntry(entry);
            }
          }
          catch (RuntimeException e) {
            LOG.warn("corrupted: " + zipFile.getName(), e);
          }
          return null;
        }

        @Override
        public InputStream getInputStream(JarFile.JarEntry entry) throws IOException {
          return zipFile.getInputStream(((MyJarEntry)entry).myEntry);
        }

        @Override
        public Enumeration<? extends JarFile.JarEntry> entries() {
          return new Enumeration<JarEntry>() {
            private final Enumeration<? extends ZipEntry> entries = zipFile.entries();

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
                LOG.warn("corrupted: " + zipFile.getName(), e);
              }
              return null;
            }
          };
        }

        @Override
        public ZipFile getZipFile() {
          return zipFile;
        }
      };
    }
    catch (IOException e) {
      LOG.warn(e.getMessage() + ": " + originalFile.getPath(), e);
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
      synchronized (lock) {
        map = SoftReference.dereference(myRelPathsToEntries);

        if (map == null) {
          JarFile zip = getJar();
          if (zip != null) {
            LogUtil.debug(LOG, "mapping %s", myBasePath);

            map = new THashMap<String, EntryInfo>();
            map.put("", new EntryInfo(null, "", true, DEFAULT_LENGTH, DEFAULT_TIMESTAMP));

            Enumeration<? extends JarFile.JarEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
              JarFile.JarEntry entry = entries.nextElement();
              if (entry == null) break;  // corrupted .jar
              getOrCreate(entry, map, zip);
            }

            myRelPathsToEntries = new SoftReference<Map<String, EntryInfo>>(Collections.unmodifiableMap(map));
          }
          else {
            map = Collections.emptyMap();
          }
        }
      }
    }
    return map;
  }

  @NotNull
  private static EntryInfo getOrCreate(JarFile.JarEntry entry, Map<String, EntryInfo> map, JarFile zip) {
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
  private static EntryInfo getOrCreate(String entryName, Map<String, EntryInfo> map, JarFile zip) {
    EntryInfo info = map.get(entryName);

    if (info == null) {
      JarFile.JarEntry entry = zip.getEntry(entryName + "/");
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
      //noinspection ConstantConditions
      LOG.info(zip.getZipFile().getName() + ": " + entryName + " should be a directory");
      info = new EntryInfo(info.parent, info.shortName, true, info.length, info.timestamp);
      map.put(entryName, info);
    }

    return info;
  }

  public long getLength(@NotNull VirtualFile file) {
    if (file.getParent() == null) return DEFAULT_LENGTH;
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
    if (file.getParent() == null) {
      return myJarFile.get() != null || getOriginalFile().exists();
    }

    return getEntryInfo(file) != null;
  }

  @Nullable
  public FileAttributes getAttributes(@NotNull VirtualFile file) {
    if (file.getParent() == null) {
      return new FileAttributes(true, false, false, false, DEFAULT_LENGTH, getOriginalFile().lastModified(), false);
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
    JarFile jar = getJar();
    if (jar != null) {
      JarFile.JarEntry entry = jar.getEntry(getRelativePath(file));
      if (entry != null) {
        InputStream stream = jar.getInputStream(entry);
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
    return ArrayUtil.EMPTY_BYTE_ARRAY;
  }
}
