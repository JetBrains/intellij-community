/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.ide.plugins;

/**
 * @author max
 */
public class HelpSetPath {
  private String file;
  private String path;

  public HelpSetPath(String file, String path) {
    this.file = file;
    this.path = path;
  }

  public String getFile() {
    return file;
  }

  public String getPath() {
    return path;
  }
}
