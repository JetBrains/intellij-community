/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs.pointers;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;

import java.util.List;

/**
 * @author dsl
 */
public interface VirtualFilePointerContainer {
  void killAll();

  void add(VirtualFile file);

  void add(String url);

  void remove(VirtualFilePointer pointer);

  List<VirtualFilePointer> getList();

  void addAll(VirtualFilePointerContainer that);

  String[] getUrls();

  VirtualFile[] getFiles();

  VirtualFile[] getDirectories();

  VirtualFilePointer findByUrl(String url);

  void clear();

  int size();

  Object get(int index);

  void readExternal(Element rootChild, String childElementName) throws InvalidDataException;

  void writeExternal(Element element, String childElementName);

  void moveUp(String url);

  void moveDown(String url);
}
