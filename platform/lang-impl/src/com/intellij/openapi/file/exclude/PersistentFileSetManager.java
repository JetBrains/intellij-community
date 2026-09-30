// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.file.exclude;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.impl.CachedFileType;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.SmartList;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A persistent {@code Map<VirtualFile, String>}.
 * <p>
 * The manager holds a loaded entry by its URL until a lookup meets the file with this URL.
 * The lookup then moves the entry to the file.
 */
@ApiStatus.Internal
public abstract class PersistentFileSetManager implements PersistentStateComponent<Element> {
  private static final String FILE_ELEMENT = "file";
  private static final String URL_ATTR = "url";
  private static final String VALUE_ATTR = "value";

  private final Object lock = new Object();
  private final Map<VirtualFile, String> resolved = new ConcurrentHashMap<>();
  private final Map<String, String> pending = new HashMap<>();
  private volatile Set<String> pendingNames = Set.of();

  boolean addFile(@NotNull VirtualFile file, @NotNull FileType type) {
    return addFiles(Collections.singletonMap(file, type));
  }

  boolean addFiles(@NotNull Map<? extends VirtualFile, FileType> files) {
    for (VirtualFile file : files.keySet()) {
      if (!(file instanceof VirtualFileWithId)) {
        //@formatter:off
        throw new IllegalArgumentException("file must be instanceof VirtualFileWithId but got: " + file + " (" + file.getClass() + ")");
      }
      if (file.isDirectory()) {
        //@formatter:off
        throw new IllegalArgumentException("file must not be directory but got: " + file + "; File.isDirectory():" + new File(file.getPath()).isDirectory());
      }
    }

    List<VirtualFile> changedFiles = new SmartList<>();
    synchronized (lock) {
      var isPendingChanged = false;
      for (Map.Entry<? extends VirtualFile, FileType> entry : files.entrySet()) {
        VirtualFile file = entry.getKey();
        String value = entry.getValue().getName();
        String prevValue = resolved.put(file, value);
        String pendingValue = pending.remove(file.getUrl());
        isPendingChanged |= pendingValue != null;
        if (!value.equals(prevValue == null ? pendingValue : prevValue)) {
          changedFiles.add(file);
        }
      }
      if (isPendingChanged) {
        updatePendingNames();
      }
    }

    boolean isAdded = !changedFiles.isEmpty();
    if (isAdded) {
      onFileSettingsChanged(changedFiles);
    }
    return isAdded;
  }

  public boolean removeFile(@NotNull VirtualFile file) {
    boolean isRemoved;
    synchronized (lock) {
      var isRemovedResolved = resolved.remove(file) != null;
      var isRemovedPending = pending.remove(file.getUrl()) != null;
      if (isRemovedPending) {
        updatePendingNames();
      }
      isRemoved = isRemovedResolved || isRemovedPending;
    }
    if (isRemoved) {
      onFileSettingsChanged(Collections.singleton(file));
    }
    return isRemoved;
  }

  @VisibleForTesting
  public @Nullable String getFileValue(@NotNull VirtualFile file) {
    if (!(file instanceof VirtualFileWithId)) {
      return null;
    }

    Set<String> names = pendingNames;
    String value = resolved.get(file);
    if (value != null || names.isEmpty() || !names.contains(file.getName())) {
      return value;
    }

    synchronized (lock) {
      value = resolved.get(file);
      if (value == null) {
        value = pending.remove(file.getUrl());
        if (value != null) {
          resolved.put(file, value);
          updatePendingNames();
        }
      }
      return value;
    }
  }

  /**
   * Call it under {@link #lock} after each change of {@link #pending}.
   */
  private void updatePendingNames() {
    if (pending.isEmpty()) {
      pendingNames = Set.of();
      return;
    }

    var names = new HashSet<String>(pending.size());
    for (String url : pending.keySet()) {
      names.add(url.substring(url.lastIndexOf('/') + 1));
    }
    pendingNames = Set.copyOf(names);
  }

  private static void onFileSettingsChanged(@NotNull Collection<? extends VirtualFile> files) {
    // later because component load could be performed in background
    ApplicationManager.getApplication().invokeLater(() -> {
      CachedFileType.clearCache();
      FileContentUtilCore.reparseFiles(files);
    });
  }

  /**
   * Returns all files of the set.
   * The method walks the VFS for each entry that no lookup has met yet, so do not call it on the EDT.
   */
  @NotNull
  Collection<VirtualFile> getFiles() {
    List<String> unresolved;
    synchronized (lock) {
      unresolved = List.copyOf(pending.keySet());
    }

    var found = new HashMap<String, VirtualFile>();
    var virtualFileManager = VirtualFileManager.getInstance();
    for (String url : unresolved) {
      VirtualFile file = virtualFileManager.findFileByUrl(url);
      if (file instanceof VirtualFileWithId) {
        found.put(url, file);
      }
    }

    synchronized (lock) {
      var isPendingChanged = false;
      for (Map.Entry<String, VirtualFile> entry : found.entrySet()) {
        String value = pending.remove(entry.getKey());
        if (value != null) {
          isPendingChanged = true;
          resolved.putIfAbsent(entry.getValue(), value);
        }
      }
      if (isPendingChanged) {
        updatePendingNames();
      }
      return List.copyOf(resolved.keySet());
    }
  }

  @Override
  public Element getState() {
    Map<String, String> sorted;
    synchronized (lock) {
      sorted = new TreeMap<>(pending);
      for (Map.Entry<VirtualFile, String> entry : resolved.entrySet()) {
        sorted.put(entry.getKey().getUrl(), entry.getValue());
      }
    }

    Element root = new Element("root");
    for (Map.Entry<String, String> entry : sorted.entrySet()) {
      Element element = new Element(FILE_ELEMENT);
      element.setAttribute(URL_ATTR, entry.getKey());
      String fileTypeName = entry.getValue();
      if (!PlainTextFileType.INSTANCE.getName().equals(fileTypeName)) {
        element.setAttribute(VALUE_ATTR, fileTypeName);
      }
      root.addContent(element);
    }
    return root;
  }

  /**
   * Loads the state without a VFS walk.
   * The method finds only a file that the VFS cache holds, and it keeps the other entries by URL.
   */
  @Override
  public void loadState(@NotNull Element state) {
    var loaded = new LinkedHashMap<String, String>();
    for (Element fileElement : state.getChildren(FILE_ELEMENT)) {
      String url = fileElement.getAttributeValue(URL_ATTR);
      if (url != null) {
        String value = fileElement.getAttributeValue(VALUE_ATTR);
        loaded.put(url, Objects.requireNonNullElse(value, PlainTextFileType.INSTANCE.getName()));
      }
    }

    var cached = new HashMap<String, VirtualFile>();
    for (String url : loaded.keySet()) {
      VirtualFile file = findCachedFile(url);
      if (file instanceof VirtualFileWithId) {
        cached.put(url, file);
      }
    }

    var toReparse = new ArrayList<VirtualFile>();
    synchronized (lock) {
      var iterator = resolved.entrySet().iterator();
      while (iterator.hasNext()) {
        var entry = iterator.next();
        VirtualFile file = entry.getKey();
        String value = loaded.remove(file.getUrl());
        if (value == null) {
          iterator.remove();
          toReparse.add(file);
        }
        else if (!value.equals(entry.getValue())) {
          entry.setValue(value);
          toReparse.add(file);
        }
      }

      pending.clear();
      for (Map.Entry<String, String> entry : loaded.entrySet()) {
        VirtualFile file = cached.get(entry.getKey());
        if (file == null) {
          pending.put(entry.getKey(), entry.getValue());
        }
        else {
          resolved.put(file, entry.getValue());
          toReparse.add(file);
        }
      }
      updatePendingNames();
    }
    onFileSettingsChanged(toReparse);
  }

  private static @Nullable VirtualFile findCachedFile(@NotNull String url) {
    String protocol = VirtualFileManager.extractProtocol(url);
    if (protocol == null) {
      return null;
    }
    if (!(VirtualFileManager.getInstance().getFileSystem(protocol) instanceof NewVirtualFileSystem fileSystem)) {
      return null;
    }
    return fileSystem.findFileByPathIfCached(VirtualFileManager.extractPath(url));
  }
}
