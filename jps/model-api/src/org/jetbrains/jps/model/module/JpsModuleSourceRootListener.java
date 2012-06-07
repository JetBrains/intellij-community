package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @author nik
 */
public interface JpsModuleSourceRootListener extends EventListener {
  void sourceRootAdded(@NotNull JpsModuleSourceRoot root);
  void sourceRootRemoved(@NotNull JpsModuleSourceRoot root);
  void sourceRootChanged(@NotNull JpsModuleSourceRoot root);
}
