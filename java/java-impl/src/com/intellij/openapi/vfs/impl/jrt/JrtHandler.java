// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.jrt;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.reference.SoftReference.dereference;

@SuppressWarnings("SynchronizeOnThis")
public class JrtHandler extends ArchiveHandler {
  private static final URI ROOT_URI = URI.create("jrt:/");

  private SoftReference<FileSystem> myFileSystem;

  public JrtHandler(@NotNull String path) {
    super(path);
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    synchronized (this) {
      var fs = dereference(myFileSystem);
      if (fs != null) {
        myFileSystem = null;
        try {
          fs.close();
        }
        catch (Exception e) {
          Logger.getInstance(JrtHandler.class).info(e);
        }
      }
    }
  }

  protected synchronized FileSystem getFileSystem() throws IOException {
    var fs = dereference(myFileSystem);
    if (fs == null) {
      var path = getFile().getPath();
      try {
        fs = FileSystems.newFileSystem(ROOT_URI, Collections.singletonMap("java.home", path));
        myFileSystem = new SoftReference<>(fs);
      }
      catch (RuntimeException | Error e) {
        throw new IOException("Error mounting JRT filesystem at " + path, e);
      }
    }
    else if (!fs.isOpen()) {
      throw new ProcessCanceledException();
    }
    return fs;
  }

  @Override
  protected @NotNull Map<String, EntryInfo> createEntriesMap() throws IOException {
    var map = new HashMap<String, EntryInfo>();
    map.put("", createRootEntry());

    var root = getFileSystem().getPath("/modules");
    if (!Files.exists(root)) throw new FileNotFoundException("JRT root missing");

    Files.walkFileTree(root, new SimpleFileVisitor<>() {
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
          var relativePath = entry.subpath(1, pathLength);
          var path = relativePath.toString();
          if (!map.containsKey(path)) {
            var parent = map.get(pathLength > 2 ? relativePath.getParent().toString() : "");
            if (parent == null) throw new IOException("Out of order: " + entry);

            var shortName = entry.getFileName().toString();
            var modified = attrs.lastModifiedTime().toMillis();
            map.put(path, new EntryInfo(shortName, attrs.isDirectory(), attrs.size(), modified, parent));
          }
        }
      }
    });

    return map;
  }

  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull String relativePath) throws IOException {
    var entry = getEntryInfo(relativePath);
    if (entry == null) throw new FileNotFoundException(getFile() + " : " + relativePath);
    try {
      var path = getFileSystem().getPath("/modules/" + relativePath);
      return Files.readAllBytes(path);
    }
    catch (RuntimeException e) {
      if (e.getCause() instanceof IOException ioe) throw ioe;
      throw e;
    }
  }
}
