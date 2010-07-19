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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.ProjectRootListener;
import com.intellij.openapi.projectRoots.ex.ProjectRoot;
import com.intellij.openapi.projectRoots.ex.ProjectRootContainer;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.PersistentOrderRootType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author mike
 */
public class ProjectRootContainerImpl implements JDOMExternalizable, ProjectRootContainer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.ProjectRootContainerImpl");
  private final Map<OrderRootType, CompositeProjectRoot> myRoots = new HashMap<OrderRootType, CompositeProjectRoot>();

  private Map<OrderRootType, VirtualFile[]> myFiles = new HashMap<OrderRootType, VirtualFile[]>();

  private boolean myInsideChange = false;
  private final List<ProjectRootListener> myListeners = ContainerUtil.createEmptyCOWList();

  private boolean myNoCopyJars = false;

  public ProjectRootContainerImpl(boolean noCopyJars) {
    myNoCopyJars = noCopyJars;

    for(OrderRootType rootType: OrderRootType.getAllTypes()) {
      myRoots.put(rootType, new CompositeProjectRoot());
      myFiles.put(rootType, VirtualFile.EMPTY_ARRAY);
    }
  }

  @NotNull
  public VirtualFile[] getRootFiles(@NotNull OrderRootType type) {
    return myFiles.get(type);
  }

  @NotNull
  public ProjectRoot[] getRoots(@NotNull OrderRootType type) {
    return myRoots.get(type).getProjectRoots();
  }

  public void startChange() {
    LOG.assertTrue(!myInsideChange);

    myInsideChange = true;
  }

  public void finishChange() {
    LOG.assertTrue(myInsideChange);
    HashMap<OrderRootType, VirtualFile[]> oldRoots = new HashMap<OrderRootType, VirtualFile[]>(myFiles);

    for (OrderRootType orderRootType: OrderRootType.getAllTypes()) {
      final VirtualFile[] roots = myRoots.get(orderRootType).getVirtualFiles();
      final boolean same = Comparing.equal(roots, oldRoots.get(orderRootType));

      myFiles.put(orderRootType, myRoots.get(orderRootType).getVirtualFiles());

      if (!same) {
        fireRootsChanged();
      }
    }

    myInsideChange = false;
  }

  public void addProjectRootContainerListener(ProjectRootListener listener) {
    myListeners.add(listener);
  }

  public void removeProjectRootContainerListener(ProjectRootListener listener) {
    myListeners.remove(listener);
  }

  private void fireRootsChanged() {
    /*
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        LOG.info("roots changed: type='" + type + "'\n    oldRoots='" + Arrays.asList(oldRoots) + "'\n    newRoots='" + Arrays.asList(newRoots) + "' ");
      }
    });
    */
    for (final ProjectRootListener listener : myListeners) {
      listener.rootsChanged();
    }
  }


  public void removeRoot(@NotNull ProjectRoot root, @NotNull OrderRootType type) {
    LOG.assertTrue(myInsideChange);
    myRoots.get(type).remove(root);
  }

  @NotNull
  public ProjectRoot addRoot(@NotNull VirtualFile virtualFile, @NotNull OrderRootType type) {
    LOG.assertTrue(myInsideChange);
    return myRoots.get(type).add(virtualFile);
  }

  public void addRoot(@NotNull ProjectRoot root, @NotNull OrderRootType type) {
    LOG.assertTrue(myInsideChange);
    myRoots.get(type).add(root);
  }

  public void removeAllRoots(@NotNull OrderRootType type ) {
    LOG.assertTrue(myInsideChange);
    myRoots.get(type).clear();
  }

  public void removeRoot(@NotNull VirtualFile root, @NotNull OrderRootType type) {
    LOG.assertTrue(myInsideChange);
    myRoots.get(type).remove(root);
  }

  public void removeAllRoots() {
    LOG.assertTrue(myInsideChange);
    for (CompositeProjectRoot myRoot : myRoots.values()) {
      myRoot.clear();
    }
  }

  public void update() {
    LOG.assertTrue(myInsideChange);
    for (CompositeProjectRoot myRoot : myRoots.values()) {
      myRoot.update();
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    for (PersistentOrderRootType type : OrderRootType.getAllPersistentTypes()) {
      read(element, type);
    }

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        myFiles = new HashMap<OrderRootType, VirtualFile[]>();
        for(OrderRootType rootType: myRoots.keySet()) {
          CompositeProjectRoot root = myRoots.get(rootType);
          if (myNoCopyJars){
            setNoCopyJars(root);
          }
          myFiles.put(rootType, root.getVirtualFiles());
        }
      }
    });

    for (OrderRootType type : OrderRootType.getAllTypes()) {
      final VirtualFile[] newRoots = getRootFiles(type);
      final VirtualFile[] oldRoots = VirtualFile.EMPTY_ARRAY;
      if (!Comparing.equal(oldRoots, newRoots)) {
        fireRootsChanged();
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    List<PersistentOrderRootType> allTypes = OrderRootType.getSortedRootTypes();
    for (PersistentOrderRootType type : allTypes) {
      write(element, type);
    }
  }

  private static void setNoCopyJars(ProjectRoot root){
    if (root instanceof SimpleProjectRoot){
      String url = ((SimpleProjectRoot)root).getUrl();
      if (JarFileSystem.PROTOCOL.equals(VirtualFileManager.extractProtocol(url))){
        String path = VirtualFileManager.extractPath(url);
        JarFileSystem.getInstance().setNoCopyJarForPath(path);
      }
    }
    else if (root instanceof CompositeProjectRoot){
      ProjectRoot[] roots = ((CompositeProjectRoot)root).getProjectRoots();
      for (ProjectRoot root1 : roots) {
        setNoCopyJars(root1);
      }
    }
  }

  private void read(Element element, PersistentOrderRootType type) throws InvalidDataException {
    Element child = element.getChild(type.getSdkRootName());
    if (child == null){
      myRoots.put(type, new CompositeProjectRoot());
      return;
    }

    List children = child.getChildren();
    LOG.assertTrue(children.size() == 1);
    myRoots.put(type, (CompositeProjectRoot)ProjectRootUtil.read((Element)children.get(0)));
  }

  private void write(Element roots, PersistentOrderRootType type) throws WriteExternalException {
    Element e = new Element(type.getSdkRootName());
    roots.addContent(e);
    final Element root = ProjectRootUtil.write(myRoots.get(type));
    if (root != null) {
      e.addContent(root);
    }
  }


  @SuppressWarnings({"HardCodedStringLiteral"})
  void readOldVersion(Element child) {
    for (final Object o : child.getChildren("root")) {
      Element root = (Element)o;
      String url = root.getAttributeValue("file");
      SimpleProjectRoot projectRoot = new SimpleProjectRoot(url);
      String type = root.getChild("property").getAttributeValue("value");

      for(PersistentOrderRootType rootType: OrderRootType.getAllPersistentTypes()) {
        if (type.equals(rootType.getOldSdkRootName())) {
          addRoot(projectRoot, rootType);
          break;
        }
      }
    }

    myFiles = new HashMap<OrderRootType, VirtualFile[]>();
    for(OrderRootType rootType: myRoots.keySet()) {
      myFiles.put(rootType, myRoots.get(rootType).getVirtualFiles());
    }
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      final VirtualFile[] oldRoots = VirtualFile.EMPTY_ARRAY;
      final VirtualFile[] newRoots = getRootFiles(type);
      if (!Comparing.equal(oldRoots, newRoots)) {
        fireRootsChanged();
      }
    }
  }

}
