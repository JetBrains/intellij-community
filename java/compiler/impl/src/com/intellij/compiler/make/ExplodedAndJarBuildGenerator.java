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
package com.intellij.compiler.make;

import com.intellij.compiler.ant.Tag;
import com.intellij.compiler.ant.ExplodedAndJarTargetParameters;
import com.intellij.compiler.ant.taskdefs.ZipFileSet;
import com.intellij.openapi.compiler.make.BuildInstruction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class ExplodedAndJarBuildGenerator {
  public static final ExtensionPointName<ExplodedAndJarBuildGenerator> EP_NAME = ExtensionPointName.create("com.intellij.explodedAndJarBuildGenerator");

  @Nullable
  public Tag[] generateTagsForExplodedTarget(@NotNull BuildInstruction instruction, @NotNull ExplodedAndJarTargetParameters parameters,
                                             final int instructionCount)
    throws Exception {
    return null;
  }

  @Nullable
  public ZipFileSet[] generateTagsForJarTarget(@NotNull BuildInstruction instruction, @NotNull ExplodedAndJarTargetParameters parameters,
                                         final Ref<Boolean> tempDirUsed) throws Exception {
    return null;
  }

  @Nullable
  public Tag[] generateJarBuildPrepareTags(@NotNull BuildInstruction instruction, @NotNull ExplodedAndJarTargetParameters parameters) throws Exception {
    return null;
  }

}
