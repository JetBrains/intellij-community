// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler;

import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CompilerTestData implements JDOMExternalizable {
  private final Set<String> myPathsToDelete = new HashSet<>();
  private String[] myDeletedByMake;
  private String[] myToRecompile;

  @Override
  public void readExternal(Element element) {
    // read paths to be deleted
    myPathsToDelete.clear();
    for (Object o3 : element.getChildren("delete")) {
      Element elem = (Element)o3;
      for (Object o : elem.getChildren()) {
        Element pathElement = (Element)o;
        myPathsToDelete.add(pathElement.getAttributeValue("path"));
      }
    }

    // read paths that are expected to be deleted
    List<String> data = new ArrayList<>();
    for (Object o2 : element.getChildren("deleted_by_make")) {
      Element elem = (Element)o2;
      for (Object o : elem.getChildren()) {
        Element pathElement = (Element)o;
        data.add(pathElement.getAttributeValue("path"));
      }
    }
    myDeletedByMake = ArrayUtil.toStringArray(data);

    // read paths that are expected to be found by dependencies
    data.clear();
    for (Element elem : element.getChildren("recompile")) {
      for (Object o : elem.getChildren()) {
        Element pathElement = (Element)o;
        data.add(pathElement.getAttributeValue("path"));
      }
    }
    myToRecompile = ArrayUtil.toStringArray(data);
  }

  @Override
  public void writeExternal(Element element) {
  }

  public String[] getDeletedByMake() {
    return myDeletedByMake;
  }

  public boolean shouldDeletePath(String path) {
    return myPathsToDelete.contains(path);
  }

  public String[] getToRecompile() {
    return myToRecompile;
  }
}
