package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.EventListener;

/**
 * @author nik
 */
public interface JpsModuleListener extends EventListener {
  void moduleAdded(@NotNull JpsModule module);
  void moduleRemoved(@NotNull JpsModule module);
}
