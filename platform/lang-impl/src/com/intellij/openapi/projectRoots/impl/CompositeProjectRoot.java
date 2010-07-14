/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.ex.ProjectRoot;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author mike
 */
class CompositeProjectRoot implements ProjectRoot {
  private final List<ProjectRoot> myRoots = new ArrayList<ProjectRoot>();

  @NotNull 
  ProjectRoot[] getProjectRoots() {
    return myRoots.toArray(new ProjectRoot[myRoots.size()]);
  }

  public String getPresentableString() {
    throw new UnsupportedOperationException();
  }

  public VirtualFile[] getVirtualFiles() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (ProjectRoot root : myRoots) {
      ContainerUtil.addAll(result, root.getVirtualFiles());
    }

    return VfsUtil.toVirtualFileArray(result);
  }

  public String[] getUrls() {
    final List<String> result = new ArrayList<String>();
    for (ProjectRoot root : myRoots) {
      ContainerUtil.addAll(result, root.getUrls());
    }
    return ArrayUtil.toStringArray(result);
  }

  public boolean isValid() {
    return true;
  }

  void remove(ProjectRoot root) {
    myRoots.remove(root);
  }

  @NotNull
  ProjectRoot add(VirtualFile virtualFile) {
    final SimpleProjectRoot root = new SimpleProjectRoot(virtualFile);
    myRoots.add(root);
    return root;
  }

  void add(ProjectRoot root) {
    myRoots.add(root);
  }

  void remove(VirtualFile root) {
    for (Iterator<ProjectRoot> iterator = myRoots.iterator(); iterator.hasNext();) {
      ProjectRoot projectRoot = iterator.next();
      if (projectRoot instanceof SimpleProjectRoot) {
        SimpleProjectRoot r = (SimpleProjectRoot)projectRoot;
        if (r.getFile() != null && r.getFile().equals(root)) {
          iterator.remove();
        }
      }
    }
  }

  void clear() {
    myRoots.clear();
  }

  public void readExternal(Element element) throws InvalidDataException {
    final List children = element.getChildren();
    for (Object aChildren : children) {
      Element e = (Element)aChildren;
      myRoots.add(ProjectRootUtil.read(e));
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (ProjectRoot root : myRoots) {
      final Element e = ProjectRootUtil.write(root);
      if (e != null) {
        element.addContent(e);
      }
    }
  }

  public void update() {
    for (ProjectRoot root : myRoots) {
      root.update();
    }
  }

}
