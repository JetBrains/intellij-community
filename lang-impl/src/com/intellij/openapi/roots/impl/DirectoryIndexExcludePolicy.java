package com.intellij.openapi.roots.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;

/**
 * @author yole
 */
public interface DirectoryIndexExcludePolicy {
  ExtensionPointName<DirectoryIndexExcludePolicy> EP_NAME = ExtensionPointName.create("com.intellij.directoryIndexExcludePolicy");

  boolean isExcludeRoot(VirtualFile file);
  boolean isExcludeRootForModule(final Module module, final VirtualFile file);
  VirtualFile[] getExcludeRootsForProject();
}
