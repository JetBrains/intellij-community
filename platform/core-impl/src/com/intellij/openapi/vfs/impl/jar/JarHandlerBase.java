/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
  private Reference<Map<String, EntryInfo>> myRelPathsToEntries = new SoftReference<Map<String, EntryInfo>>(null);
  private final Object lock = new Object();

  protected final String myBasePath;

  protected static class EntryInfo {
    protected final boolean isDirectory;
    protected final String shortName;
    protected final EntryInfo parent;

    public EntryInfo(@NotNull String shortName, final EntryInfo parent, final boolean directory) {
      this.shortName = shortName;
      this.parent = parent;
      isDirectory = directory;
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
      @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") final ZipFile zipFile = new ZipFile(getMirrorFile(originalFile));

      class MyJarEntry implements JarFile.JarEntry {
        private final ZipEntry myEntry;

        MyJarEntry(ZipEntry entry) {
          myEntry = entry;
        }

        public ZipEntry getEntry() {
          return myEntry;
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
          catch (IllegalArgumentException e) {
            LOG.warn(e);
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
              catch (IllegalArgumentException e) {
                LOG.warn(e);
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
  private static EntryInfo getOrCreate(@NotNull String entryName, boolean isDirectory, @NotNull Map<String, EntryInfo> map) {
    EntryInfo info = map.get(entryName);
    if (info == null) {
      int idx = entryName.lastIndexOf('/');
      final String parentEntryName = idx > 0 ? entryName.substring(0, idx) : "";
      String shortName = idx > 0 ? entryName.substring(idx + 1) : entryName;
      if (".".equals(shortName)) return getOrCreate(parentEntryName, true, map);

      info = new EntryInfo(shortName, getOrCreate(parentEntryName, true, map), isDirectory);
      map.put(entryName, info);
    }

    return info;
  }

  @NotNull
  public String[] list(@NotNull final VirtualFile file) {
    synchronized (lock) {
      EntryInfo parentEntry = getEntryInfo(file);

      Set<String> names = new HashSet<String>();
      for (EntryInfo info : getEntriesMap().values()) {
        if (info.parent == parentEntry) {
          names.add(info.shortName);
        }
      }

      return ArrayUtil.toStringArray(names);
    }
  }

  protected EntryInfo getEntryInfo(@NotNull VirtualFile file) {
    synchronized (lock) {
      String parentPath = getRelativePath(file);
      return getEntryInfo(parentPath);
    }
  }

  public EntryInfo getEntryInfo(@NotNull String parentPath) {
    return getEntriesMap().get(parentPath);
  }

  @NotNull
  protected Map<String, EntryInfo> getEntriesMap() {
    synchronized (lock) {
      Map<String, EntryInfo> map = SoftReference.dereference(myRelPathsToEntries);

      if (map == null) {
        final JarFile zip = getJar();
        if (zip != null) {
          LogUtil.debug(LOG, "mapping %s", myBasePath);

          map = new THashMap<String, EntryInfo>();
          map.put("", new EntryInfo("", null, true));
          final Enumeration<? extends JarFile.JarEntry> entries = zip.entries();
          while (entries.hasMoreElements()) {
            final JarFile.JarEntry entry = entries.nextElement();
            final String name = entry.getName();
            final boolean isDirectory = StringUtil.endsWithChar(name, '/');
            getOrCreate(isDirectory ? name.substring(0, name.length() - 1) : name, isDirectory, map);
          }

          myRelPathsToEntries = new SoftReference<Map<String, EntryInfo>>(map);
        }
        else {
          map = Collections.emptyMap();
        }
      }

      return map;
    }
  }

  @NotNull
  private String getRelativePath(@NotNull VirtualFile file) {
    final String path = file.getPath().substring(myBasePath.length() + 1);
    return StringUtil.startsWithChar(path, '/') ? path.substring(1) : path;
  }

  @Nullable
  private JarFile.JarEntry convertToEntry(@NotNull VirtualFile file) {
    String path = getRelativePath(file);
    final JarFile jar = getJar();
    return jar == null ? null : jar.getEntry(path);
  }

  public long getLength(@NotNull final VirtualFile file) {
    final JarFile.JarEntry entry = convertToEntry(file);
    synchronized (lock) {
      return entry == null ? DEFAULT_LENGTH : entry.getSize();
    }
  }

  @NotNull
  public InputStream getInputStream(@NotNull final VirtualFile file) throws IOException {
    return new BufferExposingByteArrayInputStream(contentsToByteArray(file));
  }

  @NotNull
  public byte[] contentsToByteArray(@NotNull final VirtualFile file) throws IOException {
    final JarFile.JarEntry entry = convertToEntry(file);
    if (entry == null) {
      return ArrayUtil.EMPTY_BYTE_ARRAY;
    }
    synchronized (lock) {
      final JarFile jar = getJar();
      assert jar != null : file;

      final InputStream stream = jar.getInputStream(entry);
      assert stream != null : file;

      try {
        return FileUtil.loadBytes(stream, (int)entry.getSize());
      }
      finally {
        stream.close();
      }
    }
  }

  public long getTimeStamp(@NotNull final VirtualFile file) {
    if (file.getParent() == null) return getOriginalFile().lastModified(); // Optimization
    final JarFile.JarEntry entry = convertToEntry(file);
    synchronized (lock) {
      return entry == null ? DEFAULT_TIMESTAMP : entry.getTime();
    }
  }

  public boolean isDirectory(@NotNull final VirtualFile file) {
    if (file.getParent() == null) return true; // Optimization
    synchronized (lock) {
      final String path = getRelativePath(file);
      final EntryInfo info = getEntryInfo(path);
      return info == null || info.isDirectory;
    }
  }

  public boolean exists(@NotNull final VirtualFile fileOrDirectory) {
    if (fileOrDirectory.getParent() == null) {
      // Optimization. Do not build entries if asked for jar root existence.
      return myJarFile.get() != null || getOriginalFile().exists();
    }

    return getEntryInfo(fileOrDirectory) != null;
  }

  @Nullable
  public FileAttributes getAttributes(@NotNull final VirtualFile file) {
    final JarFile.JarEntry entry = convertToEntry(file);
    synchronized (lock) {
      final EntryInfo entryInfo = getEntryInfo(getRelativePath(file));
      if (entryInfo == null) return null;
      final long length = entry != null ? entry.getSize() : DEFAULT_LENGTH;
      final long timeStamp = entry != null ? entry.getTime() : DEFAULT_TIMESTAMP;
      return new FileAttributes(entryInfo.isDirectory, false, false, false, length, timeStamp, false);
    }
  }
}
