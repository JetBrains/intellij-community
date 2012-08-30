package org.jetbrains.jps.model.java;

import com.intellij.openapi.util.Condition;
import org.jetbrains.jps.model.module.JpsDependenciesEnumerator;
import org.jetbrains.jps.model.module.JpsDependencyElement;

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

  @Override
  JpsJavaDependenciesEnumerator satisfying(Condition<JpsDependencyElement> condition);

  JpsJavaDependenciesEnumerator includedIn(JpsJavaClasspathKind classpathKind);

  JpsJavaDependenciesRootsEnumerator classes();
  JpsJavaDependenciesRootsEnumerator sources();
}
