package org.jetbrains.jps.model.module;

import com.intellij.openapi.util.Condition;
import com.intellij.util.Consumer;
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
  JpsDependenciesEnumerator satisfying(Condition<JpsDependencyElement> condition);

  Set<JpsModule> getModules();
  Set<JpsLibrary> getLibraries();

  void processModules(Consumer<JpsModule> consumer);
}
