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
package com.intellij.compiler.server;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Project-level extension point to dynamically vary build process parameters like classpath, bootclasspath and JVM arguments.
 */
public abstract class BuildProcessParametersProvider {
  public static final ExtensionPointName<BuildProcessParametersProvider> EP_NAME = ExtensionPointName.create("com.intellij.buildProcess.parametersProvider");

  public @NotNull List<String> getClassPath() {
    return Collections.emptyList();
  }
  
  public @NotNull List<String> getVMArguments() {
    return Collections.emptyList();
  }
  
}
