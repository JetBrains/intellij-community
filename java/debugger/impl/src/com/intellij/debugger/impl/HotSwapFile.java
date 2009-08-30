package com.intellij.debugger.impl;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * User: lex
 * Date: Nov 18, 2003
 * Time: 2:23:38 PM
 */
public class HotSwapFile {
  VirtualFile file;

  public HotSwapFile(VirtualFile file) {
    this.file = file;
  }
}
