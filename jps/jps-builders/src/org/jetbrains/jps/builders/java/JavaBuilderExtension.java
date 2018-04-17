/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.model.module.JpsModuleType;

import java.io.File;
import java.util.Collections;
import java.util.Set;

/**
 * Implement this class to customize how Java files are compiled. Implementations are registered as Java services, by creating
 * a file META-INF/services/org.jetbrains.jps.builders.java.JavaBuilderExtension containing the qualified name of your implementation class.
 *
 * @author nik
 */
public abstract class JavaBuilderExtension {
  /**
   * @return {@code true} if encoding of {@code file} should be taken into account while computing encoding for Java compilation process
   */
  public boolean shouldHonorFileEncodingForCompilation(@NotNull File file) {
    return false;
  }

  /**
   * Override this method to extend set of modules which should be processed by Java compiler.
   */
  @NotNull
  public Set<? extends JpsModuleType<?>> getCompilableModuleTypes() {
    return Collections.emptySet();
  }

  /**
   * Override this method to provide additional constant search capabilities that would augment the logic already built into the java builder
   * Results from ConstantAffectionResolver extensions will be combined with the results found by the java ConstantAffectionResolver.
   * The implementation should expect asynchronous execution.
   */
  @Nullable
  public Callbacks.ConstantAffectionResolver getConstantSearch(CompileContext context) {
    return null;
  }
}
