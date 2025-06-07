// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension;

import java.util.*;

public final class JavaModuleExternalPathsImpl extends JavaModuleExternalPaths {
  private static final String ROOT_ELEMENT = JpsJavaModelSerializerExtension.ROOT_TAG;

  private volatile Map<? extends OrderRootType, ? extends VirtualFilePointerContainer> myOrderRootPointerContainers = Map.of();
  private final JavaModuleExternalPathsImpl mySource;
  private final Project myProject;

  public JavaModuleExternalPathsImpl(Module module) {
    this(module.getProject(), null);
  }

  @NonInjectable
  private JavaModuleExternalPathsImpl(Project project, JavaModuleExternalPathsImpl source) {
    myProject = project;
    mySource = source;
    if (source != null) {
      copyContainersFrom(source);
    }
  }

  @Override
  public @NotNull ModuleExtension getModifiableModel(boolean writable) {
    return new JavaModuleExternalPathsImpl(myProject, this);
  }

  @Override
  public void commit() {
    mySource.copyContainersFrom(this);
  }

  @Override
  public String @NotNull [] getJavadocUrls() {
    final VirtualFilePointerContainer container = myOrderRootPointerContainers.get(JavadocOrderRootType.getInstance());
    return container != null ? container.getUrls() : ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  @Override
  public VirtualFile @NotNull [] getExternalAnnotationsRoots() {
    final VirtualFilePointerContainer container = myOrderRootPointerContainers.get(AnnotationOrderRootType.getInstance());
    return container != null ? container.getFiles() : VirtualFile.EMPTY_ARRAY;
  }

  @Override
  public String @NotNull [] getExternalAnnotationsUrls() {
    final VirtualFilePointerContainer container = myOrderRootPointerContainers.get(AnnotationOrderRootType.getInstance());
    return container != null ? container.getUrls() : ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  @Override
  public void setJavadocUrls(String @NotNull [] urls) {
    setRootUrls(JavadocOrderRootType.getInstance(), urls);
  }

  @Override
  public void setExternalAnnotationUrls(String @NotNull [] urls) {
    setRootUrls(AnnotationOrderRootType.getInstance(), urls);
  }

  private void setRootUrls(final OrderRootType orderRootType, final String @NotNull [] urls) {
    VirtualFilePointerContainer container = myOrderRootPointerContainers.get(orderRootType);
    if (container == null) {
      if (urls.length == 0) {
        // do not store if no container and nothing to store
        // otherwise our module extension model will be changed and it can leads to unnecessary model commit
        // (that in turn can mask issues like https://youtrack.jetbrains.com/issue/IDEA-166461)
        return;
      }

      Disposable myDisposable = myProject.getService(ProjectLevelDisposableService.class);
      container = VirtualFilePointerManager.getInstance().createContainer(myDisposable, null);
      Map<OrderRootType, VirtualFilePointerContainer> newMap = new HashMap<>(myOrderRootPointerContainers);
      newMap.put(orderRootType, container);
      myOrderRootPointerContainers = Map.copyOf(newMap);
    }
    else {
      container.clear();
    }

    for (final String url : urls) {
      container.add(url);
    }
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    Map<OrderRootType, VirtualFilePointerContainer> newMap = new HashMap<>();
    for (PersistentOrderRootType orderRootType : OrderRootType.getAllPersistentTypes()) {
      String paths = orderRootType.getModulePathsName();
      if (paths != null) {
        final Element pathsElement = element.getChild(paths);
        if (pathsElement != null && !pathsElement.getChildren(ROOT_ELEMENT).isEmpty()) {
          Disposable myDisposable = myProject.getService(ProjectLevelDisposableService.class);
          VirtualFilePointerContainer container = VirtualFilePointerManager.getInstance().createContainer(myDisposable, null);
          newMap.put(orderRootType, container);
          container.readExternal(pathsElement, ROOT_ELEMENT, false);
        }
      }
    }
    myOrderRootPointerContainers = Map.copyOf(newMap);
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    List<Element> toWrite = null;
    for (OrderRootType orderRootType : myOrderRootPointerContainers.keySet()) {
      VirtualFilePointerContainer container = myOrderRootPointerContainers.get(orderRootType);
      if (container != null && !container.isEmpty()) {
        final Element content = new Element(((PersistentOrderRootType)orderRootType).getModulePathsName());
        container.writeExternal(content, ROOT_ELEMENT, false);
        if (toWrite == null) {
          toWrite = new ArrayList<>();
        }
        toWrite.add(content);
      }
    }
    if (toWrite != null) {
      toWrite.sort(Comparator.comparing(Element::getName));
      for (Element content : toWrite) {
        element.addContent(content);
      }
    }
  }

  private void copyContainersFrom(@NotNull JavaModuleExternalPathsImpl source) {
    Disposable myDisposable = myProject.getService(ProjectLevelDisposableService.class);
    List<Map.Entry<OrderRootType, VirtualFilePointerContainer>> newEntries =
      ContainerUtil.map(source.myOrderRootPointerContainers.entrySet(), e -> Map.entry(e.getKey(), e.getValue().clone(myDisposable, null)));
    myOrderRootPointerContainers = Map.ofEntries(newEntries.toArray(new Map.Entry[0]));
  }

  @Override
  public boolean isChanged() {
    if (myOrderRootPointerContainers.size() != mySource.myOrderRootPointerContainers.size()) return true;
    for (final OrderRootType type : myOrderRootPointerContainers.keySet()) {
      final VirtualFilePointerContainer container = myOrderRootPointerContainers.get(type);
      final VirtualFilePointerContainer otherContainer = mySource.myOrderRootPointerContainers.get(type);
      if (container == null || otherContainer == null) {
        if (container != otherContainer) return true;
      }
      else {
        final String[] urls = container.getUrls();
        final String[] otherUrls = otherContainer.getUrls();
        if (urls.length != otherUrls.length) return true;
        for (int i = 0; i < urls.length; i++) {
          if (!Comparing.strEqual(urls[i], otherUrls[i])) return true;
        }
      }
    }
    return false;
  }

  @Service(Service.Level.PROJECT)
  private static final class ProjectLevelDisposableService implements Disposable {
    @Override
    public void dispose() {
    }
  }
}