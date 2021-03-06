// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.file.exclude;

import com.google.common.collect.Sets;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.vfs.*;
import gnu.trove.THashSet;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Rustam Vishnyakov
 */
class PersistentFileSetManager implements PersistentStateComponent<Element> {
  private static final String FILE_ELEMENT = "file";
  private static final String URL_ATTR = "url";

  private final Set<VirtualFile> myFiles = new HashSet<>();

  boolean addFile(@NotNull VirtualFile file) {
    if (!(file instanceof VirtualFileWithId) || file.isDirectory()) return false;
    if (myFiles.add(file)) {
      onFileSettingsChanged(Collections.singleton(file));
    }
    return true;
  }

  boolean removeFile(@NotNull VirtualFile file) {
    boolean isRemoved = myFiles.remove(file);
    if (isRemoved) {
      onFileSettingsChanged(Collections.singleton(file));
    }
    return isRemoved;
  }

  protected void onFileSettingsChanged(@NotNull Collection<? extends VirtualFile> files) {

  }

  @NotNull
  Collection<VirtualFile> getFiles() {
    return myFiles;
  }

  @Override
  public Element getState() {
    final Element root = new Element("root");
    List<VirtualFile> sortedFiles = new ArrayList<>(myFiles);
    sortedFiles.sort(Comparator.comparing(file -> file.getPath()));
    for (VirtualFile vf : sortedFiles) {
      final Element vfElement = new Element(FILE_ELEMENT);
      final Attribute filePathAttr = new Attribute(URL_ATTR, VfsUtilCore.pathToUrl(vf.getPath()));
      vfElement.setAttribute(filePathAttr);
      root.addContent(vfElement);
    }
    return root;
  }

  @Override
  public void loadState(@NotNull Element state) {
    Set<VirtualFile> oldFiles = new THashSet<>(myFiles);
    myFiles.clear();
    final VirtualFileManager vfManager = VirtualFileManager.getInstance();
    for (Element fileElement : state.getChildren(FILE_ELEMENT)) {
      final Attribute filePathAttr = fileElement.getAttribute(URL_ATTR);
      if (filePathAttr != null) {
        final String filePath = filePathAttr.getValue();
        VirtualFile vf = vfManager.findFileByUrl(filePath);
        if (vf != null) {
          myFiles.add(vf);
        }
      }
    }

    Collection<VirtualFile> toReparse = Sets.symmetricDifference(myFiles, oldFiles);
    onFileSettingsChanged(toReparse);
  }
}
