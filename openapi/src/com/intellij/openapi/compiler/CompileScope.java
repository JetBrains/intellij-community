/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * An interface describing the current compilation scope
 * Only sources that belong to the scope are compiled
 */
public interface CompileScope {
  /**
   * @param fileType the type of the files. Null should be passed if all available files are needed
   * @param inSourceOnly if true, files are searched only in directories within the scope that are marked as "sources" or "test sources" in module settings.
   * Otherwise files are searched in all directories that belong to the scope.
   * @return a list of files of given type that belong to this scope
   */
  VirtualFile[] getFiles(FileType fileType, boolean inSourceOnly);

  /**
   * @param url an VFS url. Note that actual file may not exist on the disk.
   * @return true if the url specified belongs to the scope, false otharwise.
   * Note: the method may be time-consuming.
   */
  boolean belongs(String url);

  /**
   * @return a list of modules this scope affects
   */
  Module[] getAffectedModules();
}
