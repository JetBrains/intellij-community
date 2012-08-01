package org.jetbrains.jps.model.module;

import org.jetbrains.jps.model.library.JpsLibrary;

import java.util.Set;

/**
 * @author nik
 */
public interface JpsDependenciesEnumerator {
  JpsDependenciesEnumerator withoutLibraries();
  JpsDependenciesEnumerator withoutDepModules();
  JpsDependenciesEnumerator withoutSdk();
  JpsDependenciesEnumerator withoutModuleSourceEntries();
  JpsDependenciesEnumerator recursively();
  Set<JpsModule> getModules();
  Set<JpsLibrary> getLibraries();
}
