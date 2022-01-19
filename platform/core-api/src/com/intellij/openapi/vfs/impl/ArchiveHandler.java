// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Use {@link TempCopyArchiveHandler} if you'd like to extract archive to a temporary file
 * and use it to read attributes and content.
 */
public abstract class ArchiveHandler {
  public static final long DEFAULT_LENGTH = 0L;
  public static final long DEFAULT_TIMESTAMP = -1L;
  public static final FileAttributes DIRECTORY_ATTRIBUTES =
    new FileAttributes(true, false, false, false, DEFAULT_LENGTH, DEFAULT_TIMESTAMP, false, FileAttributes.CaseSensitivity.SENSITIVE);

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

  private volatile File myPath;
  private final Object myLock = new Object();
  private volatile Reference<Map<String, EntryInfo>> myEntries = new SoftReference<>(null);
  private volatile Reference<AddonlyKeylessHash<EntryInfo, Object>> myChildrenEntries = new SoftReference<>(null);
  private boolean myCorrupted;

  protected ArchiveHandler(@NotNull String path) {
    myPath = new File(path);
  }

  public @NotNull File getFile() {
    return myPath;
  }

  protected void setFile(@NotNull File path) {
    synchronized (myLock) {
      assert myEntries.get() == null && myChildrenEntries.get() == null && !myCorrupted : "Archive already opened";
      myPath = path;
    }
  }

  public @Nullable FileAttributes getAttributes(@NotNull String relativePath) {
    if (!relativePath.isEmpty()) {
      EntryInfo e = getEntryInfo(relativePath);
      if (e != null) {
        return new FileAttributes(e.isDirectory, false, false, false, e.length, e.timestamp, false, FileAttributes.CaseSensitivity.SENSITIVE);
      }
    }
    else if (Files.exists(myPath.toPath())) {
      return DIRECTORY_ATTRIBUTES;
    }

    return null;
  }

  public String @NotNull [] list(@NotNull String relativePath) {
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
    Map<EntryInfo, List<EntryInfo>> map = new HashMap<>();
    for (EntryInfo info : getEntriesMap().values()) {
      if (info.isDirectory && !map.containsKey(info)) map.put(info, new SmartList<>());
      if (info.parent != null) {
        List<EntryInfo> parentChildren = map.get(info.parent);
        if (parentChildren == null) map.put(info.parent, parentChildren = new SmartList<>());
        parentChildren.add(info);
      }
    }

    AddonlyKeylessHash<EntryInfo, Object> result = new AddonlyKeylessHash<>(map.size(), ourKeyValueMapper);
    for (List<EntryInfo> children : map.values()) {
      int numberOfChildren = children.size();
      if (numberOfChildren == 1) {
        result.add(children.get(0));
      }
      else if (numberOfChildren > 1) {
        result.add(children.toArray(new EntryInfo[numberOfChildren]));
      }
    }
    return result;
  }

  @ApiStatus.OverrideOnly
  public void clearCaches() {
    synchronized (myLock) {
      myEntries.clear();
      myChildrenEntries.clear();
      myCorrupted = false;
    }
  }

  protected @Nullable EntryInfo getEntryInfo(@NotNull String relativePath) {
    return getEntriesMap().get(relativePath);
  }

  protected @NotNull Map<String, EntryInfo> getEntriesMap() {
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
          // createEntriesMap recreates EntryInfo instances, so we need to ensure that we also recreate the children entries
          // cache which uses EntryInfo instances as keys (otherwise the cache lookup in list() would return empty children arrays)
          myChildrenEntries = new SoftReference<>(null);
        }
      }
    }
    return map;
  }

  protected abstract @NotNull Map<String, EntryInfo> createEntriesMap() throws IOException;

  protected @NotNull EntryInfo createRootEntry() {
    return new EntryInfo("", true, DEFAULT_LENGTH, DEFAULT_TIMESTAMP, null);
  }

  /**
   * Attempts to place an entry with the given name into the entry map.
   * <p>
   * The name is normalized (backward slashes are converted into forward ones, then leading, trailing, and duplicate slashes
   * are removed); empty names and directory traversals are rejected; parent entries are created if needed.
   *
   * @param entryFun a routine for producing entry data; when {@code null}, a directory entry is created.
   */
  protected final void processEntry(@NotNull Map<String, EntryInfo> map,
                                    @NotNull String entryName,
                                    @Nullable BiFunction<@NotNull EntryInfo, @NotNull String, @NotNull ? extends EntryInfo> entryFun) {
    processEntry(map, null, entryName, entryFun);
  }

  protected final void processEntry(@NotNull Map<String, EntryInfo> map,
                                    @Nullable Logger logger,
                                    @NotNull String entryName,
                                    @SuppressWarnings("BoundedWildcard") @Nullable BiFunction<@NotNull EntryInfo, @NotNull String, @NotNull ? extends EntryInfo> entryFun) {
    String normalizedName = normalizeName(entryName);
    if (normalizedName.isEmpty() || normalizedName.contains("..") && ArrayUtil.contains("..", normalizedName.split("/"))) {
      if (logger != null) logger.trace("invalid entry: " + getFile() + "!/" + entryName);
      return;
    }

    if (entryFun == null) {
      directoryEntry(map, logger, normalizedName);
      return;
    }

    EntryInfo existing = map.get(normalizedName);
    if (existing != null) {
      if (logger != null) logger.trace("duplicate entry: " + getFile() + "!/" + normalizedName);
      return;
    }

    Pair<String, String> path = split(normalizedName);
    EntryInfo parent = directoryEntry(map, logger, path.first);
    map.put(normalizedName, entryFun.apply(parent, path.second));
  }

  @NotNull
  protected String normalizeName(@NotNull String entryName) {
    return StringUtil.trimTrailing(StringUtil.trimLeading(FileUtil.normalize(entryName), '/'), '/');
  }

  private EntryInfo directoryEntry(Map<String, EntryInfo> map, @Nullable Logger logger, String normalizedName) {
    EntryInfo entry = map.get(normalizedName);
    if (entry == null || !entry.isDirectory) {
      if (logger != null && entry != null) logger.trace("duplicate entry: " + getFile() + "!/" + normalizedName);
      if (normalizedName.isEmpty()) {
        entry = createRootEntry();
      }
      else {
        Pair<String, String> path = split(normalizedName);
        EntryInfo parent = directoryEntry(map, logger, path.first);
        entry = new EntryInfo(path.second, true, DEFAULT_LENGTH, DEFAULT_TIMESTAMP, parent);
      }
      map.put(normalizedName, entry);
    }
    return entry;
  }

  private static Pair<String, String> split(String normalizedName) {
    int p = normalizedName.lastIndexOf('/');
    String parentPath = p > 0 ? normalizedName.substring(0, p) : "";
    String shortName = p > 0 ? normalizedName.substring(p + 1) : normalizedName;
    return new Pair<>(parentPath, shortName);
  }

  /** @deprecated please use {@link #processEntry} instead to correctly handle invalid entry names */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  protected @NotNull Pair<String, String> splitPath(@NotNull String entryName) {
    return split(entryName);
  }

  public abstract byte @NotNull [] contentsToByteArray(@NotNull String relativePath) throws IOException;

  public @NotNull InputStream getInputStream(@NotNull String relativePath) throws IOException {
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
