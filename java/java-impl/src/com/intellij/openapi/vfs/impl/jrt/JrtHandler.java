/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl.jrt;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.StringInterner;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Map;

class JrtHandler extends ArchiveHandler {
  private static final URI ROOT_URI = URI.create("jrt:/");

  private static class JrtEntryInfo extends EntryInfo {
    private final String myModule;

    public JrtEntryInfo(@NotNull String shortName, @NotNull String module, long length, long timestamp, EntryInfo parent) {
      super(shortName, false, length, timestamp, parent);
      myModule = module;
    }
  }

  private SoftReference<FileSystem> myFileSystem;
  private final StringInterner myInterner = new StringInterner();

  public JrtHandler(@NotNull String path) {
    super(path);
  }

  private synchronized FileSystem getFileSystem() throws IOException {
    FileSystem fs = SoftReference.dereference(myFileSystem);
    if (fs == null) {
      if (SystemInfo.isJavaVersionAtLeast("9")) {
        fs = FileSystems.newFileSystem(ROOT_URI, Collections.singletonMap("java.home", getFile().getPath()));
      }
      else {
        URL url = new File(getFile(), "jrt-fs.jar").toURI().toURL();
        ClassLoader loader = new URLClassLoader(new URL[]{url}, null);
        fs = FileSystems.newFileSystem(ROOT_URI, Collections.emptyMap(), loader);
      }
      myFileSystem = new SoftReference<>(fs);
    }
    return fs;
  }

  @NotNull
  @Override
  protected Map<String, EntryInfo> createEntriesMap() throws IOException {
    Map<String, EntryInfo> map = ContainerUtil.newHashMap();
    map.put("", createRootEntry());

    Path root = getFileSystem().getPath("/modules");
    if (!Files.exists(root)) throw new FileNotFoundException("JRT root missing");

    Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        process(dir, attrs);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        process(file, attrs);
        return FileVisitResult.CONTINUE;
      }

      private void process(Path entry, BasicFileAttributes attrs) throws IOException {
        int pathLength = entry.getNameCount();
        if (pathLength <= 2) return;

        Path relativePath = entry.subpath(2, pathLength);
        String path = relativePath.toString(), shortName = entry.getFileName().toString();
        if (map.containsKey(path) || "module-info.class".equals(shortName)) return;

        EntryInfo parent = map.get(pathLength > 3 ? relativePath.getParent().toString() : "");
        if (parent == null) throw new IOException("Out of order: " + entry);

        long length = attrs.size();
        long modified = attrs.lastModifiedTime().toMillis();
        if (attrs.isDirectory()) {
          map.put(path, new EntryInfo(shortName, true, length, modified, parent));
        }
        else {
          String module = myInterner.intern(entry.getName(1).toString());
          map.put(path, new JrtEntryInfo(shortName, module, length, modified, parent));
        }
      }
    });

    return map;
  }

  @NotNull
  @Override
  public byte[] contentsToByteArray(@NotNull String relativePath) throws IOException {
    EntryInfo entry = getEntryInfo(relativePath);
    if (!(entry instanceof JrtEntryInfo)) throw new FileNotFoundException(getFile() + " : " + relativePath);
    Path path = getFileSystem().getPath("/modules/" + ((JrtEntryInfo)entry).myModule + '/' + relativePath);
    return Files.readAllBytes(path);
  }
}

class JrtHandlerStub extends ArchiveHandler {
  public JrtHandlerStub(@NotNull String path) {
    super(path);
  }

  @NotNull
  @Override
  protected Map<String, EntryInfo> createEntriesMap() {
    return Collections.emptyMap();
  }

  @NotNull
  @Override
  public byte[] contentsToByteArray(@NotNull String relativePath) {
    return ArrayUtil.EMPTY_BYTE_ARRAY;
  }
}