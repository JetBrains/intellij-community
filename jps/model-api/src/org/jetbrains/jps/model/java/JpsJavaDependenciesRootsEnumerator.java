package org.jetbrains.jps.model.java;

import org.jetbrains.jps.model.module.JpsDependenciesRootsEnumerator;

/**
 * @author nik
 */
public interface JpsJavaDependenciesRootsEnumerator extends JpsDependenciesRootsEnumerator {
  JpsJavaDependenciesRootsEnumerator withoutSelfModuleOutput();
}
