// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.file.exclude;

import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.impl.CachedFileType;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.SmartList;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.File;
import java.util.*;

/**
 * A persistent {@code Set<VirtualFile>} or persistent {@code Map<VirtualFile, String>}
 */
@ApiStatus.Internal
public abstract class PersistentFileSetManager implements PersistentStateComponent<Element> {
  private static final String FILE_ELEMENT = "file";
  private static final String URL_ATTR = "url";
  private static final String VALUE_ATTR = "value";

  private final Map<VirtualFile, String> myMap = new HashMap<>();

  boolean addFile(@NotNull VirtualFile file, @NotNull FileType type) {
    return addFiles(Collections.singletonMap(file, type));
  }

  boolean addFiles(@NotNull Map<? extends VirtualFile, FileType> files) {
    List<VirtualFile> changedFiles = new SmartList<>();
    for (Map.Entry<? extends VirtualFile, FileType> entry : files.entrySet()) {
      VirtualFile file = entry.getKey();
      if (!(file instanceof VirtualFileWithId)) {
        //@formatter:off
        throw new IllegalArgumentException("file must be instanceof VirtualFileWithId but got: " + file + " (" + file.getClass() + ")");
      }
      if (file.isDirectory()) {
        //@formatter:off
        throw new IllegalArgumentException("file must not be directory but got: " + file + "; File.isDirectory():" + new File(file.getPath()).isDirectory());
      }

      String value = entry.getValue().getName();
      String prevValue = myMap.put(file, value);

      if (!value.equals(prevValue)) {
        changedFiles.add(file);
      }
    }

    boolean isAdded = !changedFiles.isEmpty();
    if (isAdded) {
      onFileSettingsChanged(changedFiles);
    }
    return isAdded;
  }

  public boolean removeFile(@NotNull VirtualFile file) {
    boolean isRemoved = myMap.remove(file) != null;
    if (isRemoved) {
      onFileSettingsChanged(Collections.singleton(file));
    }
    return isRemoved;
  }

  @VisibleForTesting
  public String getFileValue(@NotNull VirtualFile file) {
    return myMap.get(file);
  }

  private static void onFileSettingsChanged(@NotNull Collection<? extends VirtualFile> files) {
    // later because component load could be performed in background
    ApplicationManager.getApplication().invokeLater(() -> {
      CachedFileType.clearCache();
      FileContentUtilCore.reparseFiles(files);
    });
  }

  @NotNull
  Collection<VirtualFile> getFiles() {
    return myMap.keySet();
  }

  @Override
  public Element getState() {
    Element root = new Element("root");
    List<Map.Entry<VirtualFile, String>> sorted = new ArrayList<>(myMap.entrySet());
    sorted.sort(Comparator.comparing(e -> e.getKey().getPath()));
    for (Map.Entry<VirtualFile, String> e : sorted) {
      Element element = new Element(FILE_ELEMENT);
      element.setAttribute(URL_ATTR, VfsUtilCore.pathToUrl(e.getKey().getPath()));
      String fileTypeName = e.getValue();
      if (fileTypeName != null && !PlainTextFileType.INSTANCE.getName().equals(fileTypeName)) {
        element.setAttribute(VALUE_ATTR, fileTypeName);
      }
      root.addContent(element);
    }
    return root;
  }

  @Override
  public void loadState(@NotNull Element state) {
    Set<VirtualFile> oldFiles = new HashSet<>(getFiles());
    myMap.clear();
    for (Element fileElement : state.getChildren(FILE_ELEMENT)) {
      Attribute urlAttr = fileElement.getAttribute(URL_ATTR);
      if (urlAttr != null) {
        String url = urlAttr.getValue();
        VirtualFile vf = VirtualFileManager.getInstance().findFileByUrl(url);
        if (vf != null) {
          String value = fileElement.getAttributeValue(VALUE_ATTR);
          myMap.put(vf, Objects.requireNonNullElse(value, PlainTextFileType.INSTANCE.getName()));
        }
      }
    }

    Collection<VirtualFile> toReparse = Sets.symmetricDifference(myMap.keySet(), oldFiles);
    onFileSettingsChanged(toReparse);
  }
}
