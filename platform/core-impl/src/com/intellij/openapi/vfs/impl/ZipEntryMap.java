// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.util.Conditions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Map of relativePath => ArchiveHandler.EntryInfo optimised for memory:
 * - it does not store keys (may be recovered from the ArchiveHandler.EntryInfo)
 * - does not support removal
 */
final class ZipEntryMap extends AbstractMap<String, ArchiveHandler.EntryInfo> {
  private ArchiveHandler.EntryInfo[] entries;
  private int size;

  ZipEntryMap(int expectedSize) {
    size = 0;
    // expectedSize is the number of entries in the zip file, and the actual number of entries in the
    // hash map will be larger because we also need to store intermediate directories which aren't
    // represented by ZipEntry instances. Therefore, choose a smaller load factor than the one used in rehash().
    entries = new ArchiveHandler.EntryInfo[Math.max(10, expectedSize * 2)];  // load factor 0.5
  }

  @Override
  public ArchiveHandler.EntryInfo get(@NotNull Object key) {
    String relativePath = (String)key;
    int index = index(relativePath, entries);
    ArchiveHandler.EntryInfo entry;
    int i = index;
    while (true) {
      entry = entries[i];
      if (entry == null || isTheOne(entry, relativePath)) break;
      if (++i == entries.length) {
        i = 0;
      }
      if (i == index) {
        entry = null;
        break;
      }
    }
    return entry;
  }

  private static int index(@NotNull String relativePath, ArchiveHandler.EntryInfo @NotNull [] entries) {
    return (relativePath.hashCode() & 0x7fffffff) % entries.length;
  }

  @Override
  public ArchiveHandler.EntryInfo put(String relativePath, ArchiveHandler.EntryInfo value) {
    if (size >= 5 * entries.length / 8) { // 0.625
      rehash();
    }

    ArchiveHandler.EntryInfo old = put(relativePath, value, entries);
    if (old == null){
      size++;
    }
    return old;
  }

  @Nullable
  private static ArchiveHandler.EntryInfo put(@NotNull String relativePath,
                                              @NotNull ArchiveHandler.EntryInfo value,
                                              ArchiveHandler.EntryInfo @NotNull [] entries) {
    int index = index(relativePath, entries);
    ArchiveHandler.EntryInfo entry;
    int i = index;
    while (true) {
      entry = entries[i];
      if (entry == null || isTheOne(entry, relativePath)) {
        entries[i] = value;
        break;
      }
      if (++i == entries.length) {
        i = 0;
      }
    }
    return entry;
  }

  private static boolean isTheOne(@NotNull ArchiveHandler.EntryInfo entry, @NotNull CharSequence relativePath) {
    int endIndex = relativePath.length();
    for (ArchiveHandler.EntryInfo e = entry; e != null; e = e.parent) {
      CharSequence shortName = e.shortName;
      if (!CharArrayUtil.regionMatches(relativePath, endIndex - shortName.length(), relativePath.length(), shortName)) {
        return false;
      }

      endIndex -= shortName.length();
      if (e.parent != null && e.parent.shortName.length() != 0 && endIndex != 0) {
        // match "/"
        if (relativePath.charAt(endIndex-1) == '/') {
          endIndex -= 1;
        }
        else {
          return false;
        }
      }

    }
    return endIndex==0;
  }

  private void rehash() {
    ArchiveHandler.EntryInfo[] newEntries = new ArchiveHandler.EntryInfo[entries.length < 1000 ? entries.length  * 2 : entries.length * 3/2];
    for (ArchiveHandler.EntryInfo entry : entries) {
      if (entry != null) {
        put(getRelativePath(entry), entry, newEntries);
      }
    }
    entries = newEntries;
  }

  @NotNull
  private static String getRelativePath(@NotNull ArchiveHandler.EntryInfo entry) {
    StringBuilder result = new StringBuilder(entry.shortName.length() + 10);
    for (ArchiveHandler.EntryInfo e = entry; e != null; e = e.parent) {
      if (result.length() != 0 && e.shortName.length() != 0) {
        result.append('/');
      }
      appendReversed(result, e.shortName);
    }
    return result.reverse().toString();
  }

  private static void appendReversed(@NotNull StringBuilder builder, @NotNull CharSequence sequence) {
    for (int i=sequence.length()-1; i>=0 ;i--) {
      builder.append(sequence.charAt(i));
    }
  }

  @Override
  public ArchiveHandler.EntryInfo remove(@NotNull Object key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public void clear() {
    size = 0;
    entries = new ArchiveHandler.EntryInfo[10];
  }

  private EntrySet entrySet;
  @NotNull
  @Override
  public EntrySet entrySet() {
    EntrySet es;
    return (es = entrySet) == null ? (entrySet = new EntrySet()) : es;
  }

  private final class EntrySet extends AbstractSet<Entry<String, ArchiveHandler.EntryInfo>> {
    @Override
    public int size() {
      return ZipEntryMap.this.size();
    }

    @Override
    public void clear() {
      ZipEntryMap.this.clear();
    }

    @Override
    public Iterator<Entry<String, ArchiveHandler.EntryInfo>> iterator() {
      return ContainerUtil.map(ContainerUtil.filter(entries, Objects::nonNull), entry -> (Entry<String, ArchiveHandler.EntryInfo>)new SimpleEntry<>(getRelativePath(entry), entry)).iterator();
    }

    @Override
    public boolean contains(Object o) {
      if (!(o instanceof Map.Entry)) {
        return false;
      }
      Map.Entry<?, ?> e = (Map.Entry<?, ?>)o;
      String key = (String)e.getKey();
      ArchiveHandler.EntryInfo value = (ArchiveHandler.EntryInfo)e.getValue();
      return value.equals(get(key));
    }

    @Override
    public boolean remove(Object o) {
      throw new UnsupportedOperationException();
    }
  }

  @NotNull
  @Override
  public Collection<ArchiveHandler.EntryInfo> values() {
    return ContainerUtil.filter(entries, Conditions.notNull());
  }
}
