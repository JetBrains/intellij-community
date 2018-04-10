// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.jrt;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Map;

@SuppressWarnings("SynchronizeOnThis")
class JrtHandler extends ArchiveHandler {
  private static final URI ROOT_URI = URI.create("jrt:/");

  private SoftReference<FileSystem> myFileSystem;

  public JrtHandler(@NotNull String path) {
    super(path);
  }

  @Override
  public void dispose() {
    super.dispose();

    synchronized (this) {
      FileSystem fs = SoftReference.dereference(myFileSystem);
      if (fs != null) {
        myFileSystem = null;
        try {
          fs.close();
          ClassLoader loader = fs.getClass().getClassLoader();
          if (loader instanceof MyClassLoader) {
            ((MyClassLoader)loader).close();
          }
        }
        catch (IOException e) {
          Logger.getInstance(JrtHandler.class).info(e);
        }
      }
    }
  }

  private synchronized FileSystem getFileSystem() throws IOException {
    FileSystem fs = SoftReference.dereference(myFileSystem);
    if (fs == null) {
      String path = getFile().getPath();
      try {
        if (SystemInfo.IS_AT_LEAST_JAVA9) {
          fs = FileSystems.newFileSystem(ROOT_URI, Collections.singletonMap("java.home", path));
        }
        else {
          File file = new File(path, "lib/jrt-fs.jar");
          if (!file.exists()) throw new IOException("Missing provider: " + file);
          fs = FileSystems.newFileSystem(ROOT_URI, Collections.emptyMap(), new MyClassLoader(file));
        }
        myFileSystem = new SoftReference<>(fs);
      }
      catch (RuntimeException | Error e) {
        throw new IOException("Error mounting JRT filesystem at " + path, e);
      }
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
        if (pathLength > 1) {
          Path relativePath = entry.subpath(1, pathLength);
          String path = relativePath.toString();
          if (!map.containsKey(path)) {
            EntryInfo parent = map.get(pathLength > 2 ? relativePath.getParent().toString() : "");
            if (parent == null) throw new IOException("Out of order: " + entry);

            String shortName = entry.getFileName().toString();
            long modified = attrs.lastModifiedTime().toMillis();
            map.put(path, new EntryInfo(shortName, attrs.isDirectory(), attrs.size(), modified, parent));
          }
        }
      }
    });

    return map;
  }

  @NotNull
  @Override
  public byte[] contentsToByteArray(@NotNull String relativePath) throws IOException {
    EntryInfo entry = getEntryInfo(relativePath);
    if (entry == null) throw new FileNotFoundException(getFile() + " : " + relativePath);
    Path path = getFileSystem().getPath("/modules/" + relativePath);
    return Files.readAllBytes(path);
  }

  private static class MyClassLoader extends URLClassLoader {
    private MyClassLoader(File file) throws MalformedURLException {
      super(new URL[]{file.toURI().toURL()}, null);
    }
  }
}