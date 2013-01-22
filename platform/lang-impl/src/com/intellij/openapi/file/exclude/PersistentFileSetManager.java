/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.file.exclude;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.HashSet;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Rustam Vishnyakov
 */
public class PersistentFileSetManager implements PersistentStateComponent<Element> {
  private static final String FILE_ELEMENT = "file";
  private static final String PATH_ATTR = "url";

  private final Set<VirtualFile> myFiles = new HashSet<VirtualFile>();
  
  protected boolean addFile(VirtualFile file) {
    if (file.isDirectory()) return false;
    myFiles.add(file);
    return true;
  }
  
  protected boolean containsFile(VirtualFile file) {
    return myFiles.contains(file);
  }
  
  protected boolean removeFile(VirtualFile file) {
    if (!myFiles.contains(file)) return false;
    myFiles.remove(file);
    return true;
  }

  @NotNull
  public Collection<VirtualFile> getFiles() {
    return myFiles;
  }
  
  public Collection<VirtualFile> getSortedFiles() {
    List<VirtualFile> sortedFiles = new ArrayList<VirtualFile>();
    sortedFiles.addAll(myFiles);
    Collections.sort(sortedFiles, new Comparator<VirtualFile>() {
      @Override
      public int compare(final VirtualFile file1, final VirtualFile file2) {
        return file1.getPath().toLowerCase().compareTo(file2.getPath().toLowerCase());
      }
    });
    return sortedFiles;
  }
  
  @Override
  public Element getState() {
    final Element root = new Element("root");
    for (VirtualFile vf : getSortedFiles()) {
      final Element vfElement = new Element(FILE_ELEMENT);
      final Attribute filePathAttr = new Attribute(PATH_ATTR, VfsUtilCore.pathToUrl(vf.getPath()));
      vfElement.setAttribute(filePathAttr);
      root.addContent(vfElement);
    }
    return root;
  }

  @Override
  public void loadState(Element state) {
    final VirtualFileManager vfManager = VirtualFileManager.getInstance();
    for (Object child : state.getChildren(FILE_ELEMENT)) {
      if (child instanceof Element) {
        final Element fileElement = (Element)child;
        final Attribute filePathAttr = fileElement.getAttribute(PATH_ATTR);
        if (filePathAttr != null) {
          final String filePath = filePathAttr.getValue();
          VirtualFile vf = vfManager.findFileByUrl(filePath);
          if (vf != null) {
            myFiles.add(vf);
          }
        }
      }
    }
  }
  
}
