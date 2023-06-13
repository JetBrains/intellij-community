/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.debugger;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Compilers of some java-based languages (like Scala) produce classes with names different from those declared in sources.
 * If for some language qualified names of compiled classes differ from names declared in the sources, a NameMapper must be registered
 * within the DebuggerManager class.
 * Multiple registered mappers forms a "chain of responsibility" and the first non-null result is returned.
 * If no mappers are registered or all mappers returned null, the result of
 * PsiClass.getQualifiedName() will be used as a qualified name of the compiled class
 */
public interface NameMapper {
  ExtensionPointName<NameMapper> EP_NAME = ExtensionPointName.create("com.intellij.debugger.nameMapper");

  /**
   * @param aClass a top-level class
   * @return a qualified name of the corresponding compiled class or null if default mechanism of getting qualified names must be used
   */
  @Nullable

  String getQualifiedName(@NotNull PsiClass aClass);

  /**
   * @param aClass a top-level class
   * @return an alternative JVM name of the corresponding compiled class or null if default mechanism of getting JVM names must be used
   */
  @Nullable
  default String getAlternativeJvmName(@NotNull PsiClass aClass) {
    return null;
  }
}
