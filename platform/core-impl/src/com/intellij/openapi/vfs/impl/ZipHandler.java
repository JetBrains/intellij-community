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
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.ZipFileCache;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipHandler extends ArchiveHandler {
  public ZipHandler(@NotNull String path) {
    super(path);
  }

  @NotNull
  @Override
  protected Map<String, EntryInfo> createEntriesMap() throws IOException {
    Map<String, EntryInfo> map = new THashMap<String, EntryInfo>();
    map.put("", createRootEntry());

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

    return map;
  }

  @NotNull
  protected File getFileToUse() {
    return getFile();
  }

  @NotNull
  private ZipFile getZipFile() throws IOException {
    return ZipFileCache.acquire(getFileToUse().getPath());
  }

  @NotNull
  private EntryInfo getOrCreate(ZipEntry entry, Map<String, EntryInfo> map, ZipFile zip) {
    boolean isDirectory = entry.isDirectory();
    String entryName = entry.getName();
    if (StringUtil.endsWithChar(entryName, '/')) {
      entryName = entryName.substring(0, entryName.length() - 1);
      isDirectory = true;
    }

    EntryInfo info = map.get(entryName);
    if (info != null) return info;

    Pair<String, String> path = splitPath(entryName);
    EntryInfo parentInfo = getOrCreate(path.first, map, zip);
    if (".".equals(path.second)) {
      return parentInfo;
    }
    info = new EntryInfo(parentInfo, path.second, isDirectory, entry.getSize(), entry.getTime());
    map.put(entryName, info);
    return info;
  }

  @NotNull
  private EntryInfo getOrCreate(String entryName, Map<String, EntryInfo> map, ZipFile zip) {
    EntryInfo info = map.get(entryName);

    if (info == null) {
      ZipEntry entry = zip.getEntry(entryName + "/");
      if (entry != null) {
        return getOrCreate(entry, map, zip);
      }

      Pair<String, String> path = splitPath(entryName);
      EntryInfo parentInfo = getOrCreate(path.first, map, zip);
      info = new EntryInfo(parentInfo, path.second, true, DEFAULT_LENGTH, DEFAULT_TIMESTAMP);
      map.put(entryName, info);
    }

    if (!info.isDirectory) {
      Logger.getInstance(getClass()).info(zip.getName() + ": " + entryName + " should be a directory");
      info = new EntryInfo(info.parent, info.shortName, true, info.length, info.timestamp);
      map.put(entryName, info);
    }

    return info;
  }

  @NotNull
  @Override
  public byte[] contentsToByteArray(@NotNull String relativePath) throws IOException {
    ZipFile zip = getZipFile();
    try {
      ZipEntry entry = zip.getEntry(relativePath);
      if (entry != null) {
        InputStream stream = zip.getInputStream(entry);
        if (stream != null) {
          // ZipFile.c#Java_java_util_zip_ZipFile_read reads data in 8K (stack allocated) blocks
          // no sense to create BufferedInputStream
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
}
