package com.intellij.lang;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public interface PerFileMappings<T> {
  
  Map<VirtualFile, T> getMappings();

  void setMappings(Map<VirtualFile, T> mappings);

  Collection<T> getAvailableValues(final VirtualFile file);

  @Nullable
  T getMapping(final VirtualFile file);

}
