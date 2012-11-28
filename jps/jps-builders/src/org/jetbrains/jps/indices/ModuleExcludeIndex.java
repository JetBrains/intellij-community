package org.jetbrains.jps.indices;

import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.Collection;

/**
 * @author nik
 */
public interface ModuleExcludeIndex {
  boolean isExcluded(File file);

  Collection<File> getModuleExcludes(JpsModule module);

  boolean isInContent(File file);
}
