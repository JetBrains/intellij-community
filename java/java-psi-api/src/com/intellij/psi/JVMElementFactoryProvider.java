/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.openapi.project.Project;

/**
 * A JVM language-specific extension to provide a factory to create common program elements like class, interface, field, etc.
 *
 * @author Medvedev Max
 */
public interface JVMElementFactoryProvider {
  /**
   * @param project current project
   * @return a factory capable to create elements in a given project
   */
  JVMElementFactory getFactory(Project project);
}
