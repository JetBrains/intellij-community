package org.jetbrains.jps.model.java;

import org.jetbrains.jps.model.module.JpsDependenciesEnumerator;

/**
 * @author nik
 */
public interface JpsJavaDependenciesEnumerator extends JpsDependenciesEnumerator {
  JpsJavaDependenciesEnumerator productionOnly();
  JpsJavaDependenciesEnumerator compileOnly();
  JpsJavaDependenciesEnumerator runtimeOnly();
  JpsJavaDependenciesEnumerator exportedOnly();

  JpsJavaDependenciesEnumerator withoutLibraries();
  JpsJavaDependenciesEnumerator withoutDepModules();
  JpsJavaDependenciesEnumerator withoutSdk();
  JpsJavaDependenciesEnumerator withoutModuleSourceEntries();

  @Override
  JpsJavaDependenciesEnumerator recursively();

  JpsJavaDependenciesEnumerator includedIn(JpsJavaClasspathKind classpathKind);

  JpsJavaDependenciesRootsEnumerator classes();
  JpsJavaDependenciesRootsEnumerator sources();
}
