/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.text.ByteArrayCharSequence;
import gnu.trove.THashMap;
import gnu.trove.TObjectObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class ArchiveHandler {
  public static final long DEFAULT_LENGTH = 0L;
  public static final long DEFAULT_TIMESTAMP = -1L;

  protected static class EntryInfo {
    public final EntryInfo parent;
    public final CharSequence shortName;
    public final boolean isDirectory;
    public final long length;
    public final long timestamp;

    public EntryInfo(@NotNull CharSequence shortName, boolean isDirectory, long length, long timestamp, @Nullable EntryInfo parent) {
      this.parent = parent;
      this.shortName = shortName;
      this.isDirectory = isDirectory;
      this.length = length;
      this.timestamp = timestamp;
    }
  }

  private final File myPath;
  private final Object myLock = new Object();
  private volatile Reference<Map<String, EntryInfo>> myEntries = new SoftReference<>(null);
  private volatile Reference<AddonlyKeylessHash<EntryInfo, Object>> myChildrenEntries = new SoftReference<>(null);
  private boolean myCorrupted;

  protected ArchiveHandler(@NotNull String path) {
    myPath = new File(path);
  }

  @NotNull
  public File getFile() {
    return myPath;
  }

  @Nullable
  public FileAttributes getAttributes(@NotNull String relativePath) {
    if (relativePath.isEmpty()) {
      FileAttributes attributes = FileSystemUtil.getAttributes(myPath);
      return attributes != null ? new FileAttributes(true, false, false, false, DEFAULT_LENGTH, DEFAULT_TIMESTAMP, false) : null;
    }
    else {
      EntryInfo entry = getEntryInfo(relativePath);
      return entry != null ? new FileAttributes(entry.isDirectory, false, false, false, entry.length, entry.timestamp, false) : null;
    }
  }

  @NotNull
  public String[] list(@NotNull String relativePath) {
    EntryInfo entry = getEntryInfo(relativePath);
    if (entry == null || !entry.isDirectory) return ArrayUtil.EMPTY_STRING_ARRAY;

    AddonlyKeylessHash<EntryInfo, Object> result = getParentChildrenMap();

    Object o = result.get(entry);
    if (o == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY; // directories without children
    }
    if (o instanceof EntryInfo) {
      return new String[] {((EntryInfo)o).shortName.toString()};
    }
    EntryInfo[] infos = (EntryInfo[])o;

    String[] names = new String[infos.length];
    for (int i = 0; i < infos.length; ++i) {
      names[i] = infos[i].shortName.toString();
    }
    return names;
  }

  @NotNull
  private AddonlyKeylessHash<EntryInfo, Object> getParentChildrenMap() {
    AddonlyKeylessHash<EntryInfo, Object> map = SoftReference.dereference(myChildrenEntries);
    if (map == null) {
      synchronized (myLock) {
        map = SoftReference.dereference(myChildrenEntries);

        if (map == null) {
          if (myCorrupted) {
            map = new AddonlyKeylessHash<>(ourKeyValueMapper);
          }
          else {
            try {
              map = createParentChildrenMap();
            }
            catch (Exception e) {
              myCorrupted = true;
              Logger.getInstance(getClass()).warn(e.getMessage() + ": " + myPath, e);
              map = new AddonlyKeylessHash<>(ourKeyValueMapper);
            }
          }

          myChildrenEntries = new SoftReference<>(map);
        }
      }
    }
    return map;
  }

  private AddonlyKeylessHash<EntryInfo, Object> createParentChildrenMap() {
    THashMap<EntryInfo, List<EntryInfo>> map = new THashMap<>();
    for (EntryInfo info : getEntriesMap().values()) {
      if (info.isDirectory && !map.containsKey(info)) map.put(info, new SmartList<>());
      if (info.parent != null) {
        List<EntryInfo> parentChildren = map.get(info.parent);
        if (parentChildren == null) map.put(info.parent, parentChildren = new SmartList<>());
        parentChildren.add(info);
      }
    }

    final AddonlyKeylessHash<EntryInfo, Object> result = new AddonlyKeylessHash<>(map.size(), ourKeyValueMapper);
    map.forEachEntry(new TObjectObjectProcedure<EntryInfo, List<EntryInfo>>() {
      @Override
      public boolean execute(EntryInfo a, List<EntryInfo> b) {
        int numberOfChildren = b.size();
        if (numberOfChildren == 1) {
          result.add(b.get(0));
        }
        else if (numberOfChildren > 1) {
          result.add(b.toArray(new EntryInfo[numberOfChildren]));
        }
        return true;
      }
    });
    return result;
  }

  public void dispose() {
    clearCaches();
  }

  protected void clearCaches() {
    synchronized (myLock) {
      myEntries.clear();
      myChildrenEntries.clear();
    }
  }

  @Nullable
  protected EntryInfo getEntryInfo(@NotNull String relativePath) {
    return getEntriesMap().get(relativePath);
  }

  @NotNull
  protected Map<String, EntryInfo> getEntriesMap() {
    Map<String, EntryInfo> map = SoftReference.dereference(myEntries);
    if (map == null) {
      synchronized (myLock) {
        map = SoftReference.dereference(myEntries);

        if (map == null) {
          if (myCorrupted) {
            map = Collections.emptyMap();
          }
          else {
            try {
              map = createEntriesMap();
            }
            catch (Exception e) {
              myCorrupted = true;
              Logger.getInstance(getClass()).warn(e.getMessage() + ": " + myPath, e);
              map = Collections.emptyMap();
            }
          }

          myEntries = new SoftReference<>(map);
        }
      }
    }
    return map;
  }

  @NotNull
  protected abstract Map<String, EntryInfo> createEntriesMap() throws IOException;

  @NotNull
  protected EntryInfo createRootEntry() {
    return new EntryInfo("", true, DEFAULT_LENGTH, DEFAULT_TIMESTAMP, null);
  }

  @NotNull
  protected EntryInfo getOrCreate(@NotNull Map<String, EntryInfo> map, @NotNull String entryName) {
    EntryInfo entry = map.get(entryName);
    if (entry == null) {
      Pair<String, String> path = splitPath(entryName);
      EntryInfo parentEntry = getOrCreate(map, path.first);
      CharSequence shortName = ByteArrayCharSequence.convertToBytesIfAsciiString(path.second);
      entry = new EntryInfo(shortName, true, DEFAULT_LENGTH, DEFAULT_TIMESTAMP, parentEntry);
      map.put(entryName, entry);
    }
    return entry;
  }

  @NotNull
  protected Pair<String, String> splitPath(@NotNull String entryName) {
    int p = entryName.lastIndexOf('/');
    String parentName = p > 0 ? entryName.substring(0, p) : "";
    String shortName = p > 0 ? entryName.substring(p + 1) : entryName;
    return Pair.create(parentName, shortName);
  }

  @NotNull
  public abstract byte[] contentsToByteArray(@NotNull String relativePath) throws IOException;

  @NotNull
  public InputStream getInputStream(@NotNull String relativePath) throws IOException {
    return new BufferExposingByteArrayInputStream(contentsToByteArray(relativePath));
  }

  private static final AddonlyKeylessHash.KeyValueMapper<EntryInfo, Object> ourKeyValueMapper = new AddonlyKeylessHash.KeyValueMapper<EntryInfo, Object>() {
    @Override
    public int hash(EntryInfo info) {
      return System.identityHashCode(info);
    }

    @Override
    public EntryInfo key(Object o) {
      if (o instanceof EntryInfo) return ((EntryInfo)o).parent;
      return ((EntryInfo[])o)[0].parent;
    }
  };
}