/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.impl.packagingCompiler;

import java.util.List;

/**
 * @author nik
 */
public class ManifestFileInfo {
  private String myOutputPath;
  private List<String> myClasspath;

  public ManifestFileInfo(final String outputPath, final List<String> classpath) {
    myOutputPath = outputPath;
    myClasspath = classpath;
  }

  public String getOutputPath() {
    return myOutputPath;
  }

  public List<String> getClasspath() {
    return myClasspath;
  }
}
