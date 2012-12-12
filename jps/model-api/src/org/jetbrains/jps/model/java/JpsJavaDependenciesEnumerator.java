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
