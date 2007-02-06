/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.compiler.make;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.extensions.ExtensionPointName;

import java.io.File;

public abstract class BuildParticipant {
  public static final ExtensionPointName<BuildParticipant> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.compiler.buildParticipant");


  public abstract void registerBuildInstructions(Module module, BuildRecipe buildRecipe, CompileContext context);


  public void afterJarCreated(File jarFile, Module module, CompileContext context) throws Exception {
  }

  public void afterExplodedCreated(File outputDir, Module module, CompileContext context) throws Exception {
  }

  public void buildFinished(Module module, CompileContext context) throws Exception {
  }

  public void runValidators(File output, final Module module, CompileContext context) throws Exception {
  }

}
