/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class JarInfo {
  private List<Pair<String, VirtualFile>> myContent;
  private List<DestinationInfo> myDestinations;
  private List<String> myClasspath;

  public JarInfo() {
    this(null);
  }

  public JarInfo(List<String> classpath) {
    myDestinations = new ArrayList<DestinationInfo>();
    myContent = new ArrayList<Pair<String, VirtualFile>>();
    myClasspath = classpath;
  }

  public void addDestination(DestinationInfo info) {
    myDestinations.add(info);
  }

  public void addContent(String pathInJar, VirtualFile sourceFile) {
    myContent.add(Pair.create(pathInJar, sourceFile));
  }

  public List<Pair<String, VirtualFile>> getContent() {
    return myContent;
  }

  public List<JarDestinationInfo> getJarDestinations() {
    final ArrayList<JarDestinationInfo> list = new ArrayList<JarDestinationInfo>();
    for (DestinationInfo destination : myDestinations) {
      if (destination instanceof JarDestinationInfo) {
        list.add((JarDestinationInfo)destination);
      }
    }
    return list;
  }

  @Nullable
  public List<String> getClasspath() {
    return myClasspath;
  }

  public List<DestinationInfo> getAllDestinations() {
    return myDestinations;
  }

  public String getPresentableDestination() {
    return !myDestinations.isEmpty() ? myDestinations.get(0).getOutputPath() : "";
  }
}
