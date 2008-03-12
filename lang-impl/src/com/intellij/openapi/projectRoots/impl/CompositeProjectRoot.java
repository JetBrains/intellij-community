package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.ex.ProjectRoot;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author mike
 */
class CompositeProjectRoot implements ProjectRoot {
  private List<ProjectRoot> myRoots = new ArrayList<ProjectRoot>();

  ProjectRoot[] getProjectRoots() {
    return myRoots.toArray(new ProjectRoot[myRoots.size()]);
  }

  public String getPresentableString() {
    throw new UnsupportedOperationException();
  }

  public VirtualFile[] getVirtualFiles() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (Iterator<ProjectRoot> iterator = myRoots.iterator(); iterator.hasNext();) {
      ProjectRoot root = iterator.next();
      result.addAll(Arrays.asList(root.getVirtualFiles()));
    }

    return result.toArray(new VirtualFile[result.size()]);
  }

  public String[] getUrls() {
    final List<String> result = new ArrayList<String>();
    for (ProjectRoot root : myRoots) {
      result.addAll(Arrays.asList(root.getUrls()));
    }
    return result.toArray(new String[result.size()]);
  }

  public boolean isValid() {
    return true;
  }

  void remove(ProjectRoot root) {
    myRoots.remove(root);
  }

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
    for (Iterator iterator = children.iterator(); iterator.hasNext();) {
      Element e = (Element)iterator.next();
      myRoots.add(ProjectRootUtil.read(e));
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (Iterator<ProjectRoot> iterator = myRoots.iterator(); iterator.hasNext();) {
      ProjectRoot root = iterator.next();
      final Element e = ProjectRootUtil.write(root);
      if (e != null) {
        element.addContent(e);
      }
    }
  }

  public void update() {
    for (Iterator<ProjectRoot> iterator = myRoots.iterator(); iterator.hasNext();) {
      ProjectRoot root = iterator.next();
      root.update();
    }
  }

}
