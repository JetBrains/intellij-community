package org.jetbrains.jps.model.library;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @author nik
 */
public interface JpsLibraryListener extends EventListener {
  void libraryAdded(@NotNull JpsLibrary library);
  void libraryRemoved(@NotNull JpsLibrary library);
}
