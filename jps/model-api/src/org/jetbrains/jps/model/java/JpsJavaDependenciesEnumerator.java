/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.model.java;

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsDependenciesEnumerator;
import org.jetbrains.jps.model.module.JpsDependencyElement;

/**
 * Interface for convenient processing dependencies of a java module or a java project. Allows to process {@link org.jetbrains.jps.model.module.JpsDependencyElement}s and collect classes
  * and source roots.
 * <p/>
 * Use {@link org.jetbrains.jps.model.java.JpsJavaExtensionService#dependencies(org.jetbrains.jps.model.module.JpsModule)} to process dependencies of a module
 * and use {@link org.jetbrains.jps.model.java.JpsJavaExtensionService#dependencies(org.jetbrains.jps.model.JpsProject)} to process dependencies of all modules in a project.<p>
 *
 * Note that all configuration methods modify {@link org.jetbrains.jps.model.module.JpsDependenciesEnumerator} instance instead of creating a new one.
 *
 * @author nik
 */
public interface JpsJavaDependenciesEnumerator extends JpsDependenciesEnumerator {
  /**
   * Skip test dependencies
   *
   * @return this instance
   */
  @NotNull
  JpsJavaDependenciesEnumerator productionOnly();

  /**
   * Skip runtime-only dependencies
   *
   * @return this instance
   */
  @NotNull
  JpsJavaDependenciesEnumerator compileOnly();

  /**
   * Skip compile-only dependencies
   *
   * @return this instance
   */
  @NotNull
  JpsJavaDependenciesEnumerator runtimeOnly();

  /**
   * Skip not exported dependencies. If this method is called after {@link #recursively()} direct non-exported dependencies won't be skipped
   *
   * @return this instance
   */
  @NotNull
  JpsJavaDependenciesEnumerator exportedOnly();

  @NotNull
  @Override
  JpsJavaDependenciesEnumerator recursively();

  /**
   * Process all direct dependencies and recursively process transitive dependencies which marked with 'exported'
   *
   * @return this instance
   */
  @NotNull
  JpsJavaDependenciesEnumerator recursivelyExportedOnly();


  @NotNull
  JpsJavaDependenciesEnumerator withoutLibraries();
  @NotNull
  JpsJavaDependenciesEnumerator withoutDepModules();
  @NotNull
  JpsJavaDependenciesEnumerator withoutSdk();
  @NotNull
  JpsJavaDependenciesEnumerator withoutModuleSourceEntries();

  @NotNull
  @Override
  JpsJavaDependenciesEnumerator satisfying(@NotNull Condition<JpsDependencyElement> condition);

  /**
   * Process only dependencies which should be included in the classpath specified by {@code classpathKind} parameter
   * @param classpathKind
   * @return this instance
   */
  @NotNull
  JpsJavaDependenciesEnumerator includedIn(@NotNull JpsJavaClasspathKind classpathKind);

  /**
   * @return enumerator for processing classes roots of the dependencies
   */
  @NotNull
  JpsJavaDependenciesRootsEnumerator classes();

  /**
   * @return enumerator for processing source roots of the dependencies
   */
  @NotNull
  JpsJavaDependenciesRootsEnumerator sources();

  /**
   *
   * @return enumerator for processing annotation roots of the dependencies
   */
  @NotNull
  JpsJavaDependenciesRootsEnumerator annotations();
}
