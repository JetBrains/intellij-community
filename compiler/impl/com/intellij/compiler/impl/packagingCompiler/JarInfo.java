/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author nik
 */
public class JarInfo {
  private List<Pair<String, VirtualFile>> myPackedFiles;
  private LinkedHashSet<Pair<String, JarInfo>> myPackedJars;
  private List<DestinationInfo> myDestinations;
  private List<String> myClasspath;

  public JarInfo() {
    this(null);
  }

  public JarInfo(List<String> classpath) {
    myDestinations = new ArrayList<DestinationInfo>();
    myPackedFiles = new ArrayList<Pair<String, VirtualFile>>();
    myPackedJars = new LinkedHashSet<Pair<String, JarInfo>>();
    myClasspath = classpath;
  }

  public void addDestination(DestinationInfo info) {
    myDestinations.add(info);
    if (info instanceof JarDestinationInfo) {
      JarDestinationInfo destinationInfo = (JarDestinationInfo)info;
      destinationInfo.getJarInfo().myPackedJars.add(Pair.create(destinationInfo.getPathInJar(), this));
    }
  }

  public void addContent(String pathInJar, VirtualFile sourceFile) {
    myPackedFiles.add(Pair.create(pathInJar, sourceFile));
  }

  public List<Pair<String, VirtualFile>> getPackedFiles() {
    return myPackedFiles;
  }

  public LinkedHashSet<Pair<String, JarInfo>> getPackedJars() {
    return myPackedJars;
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
