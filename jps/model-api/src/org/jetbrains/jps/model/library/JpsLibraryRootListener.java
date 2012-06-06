package org.jetbrains.jps.model.library;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @author nik
 */
public interface JpsLibraryRootListener extends EventListener {
  void rootAdded(@NotNull JpsLibraryRoot root);
  void rootRemoved(@NotNull JpsLibraryRoot root);
}
