/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 15, 2007
 */
public class CompilerDirectoryTimestamp extends FileAttribute {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.CompilerDirectoryTimestamp");
  
  private static final CompilerDirectoryTimestamp INSTANCE = new CompilerDirectoryTimestamp();

  private CompilerDirectoryTimestamp() {
    super("_compiler_stamp_", 1);
  }

  public static boolean isUpToDate(Collection<VirtualFile> files) {
    try {
      for (VirtualFile file : files) {
        final DataInputStream stream = INSTANCE.readAttribute(file);
        if (stream == null) {
          return false;
        }
        try {
          final long savedStamp = stream.readLong();
          if (savedStamp != file.getTimeStamp()) {
            return false;
          }
        }
        finally {
          stream.close();
        }
      }
    }
    catch (IOException e) {
      LOG.info(e);
      return false;
    }
    return true;
  }
  
  public static void updateTimestamp(Collection<VirtualFile> files) {
    try {
      for (VirtualFile file : files) {
        final DataOutputStream stream = INSTANCE.writeAttribute(file);
        try {
          stream.writeLong(file.getTimeStamp());
        }
        finally {
          stream.close();
        }
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }
}
