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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.compiler.CompileContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public abstract class ModuleBuildProperties implements ModuleComponent {
  @Nullable
  public static ModuleBuildProperties getInstance(Module module) {
    return module.getComponent(ModuleBuildProperties.class);
  }

  @NonNls
  public abstract String getArchiveExtension();

  public abstract String getJarPath();

  public abstract String getExplodedPath();

  @NotNull
  public abstract Module getModule();

  public abstract boolean isJarEnabled();

  public abstract boolean isExplodedEnabled();

  public abstract boolean isBuildOnFrameDeactivation();

  public abstract boolean isSyncExplodedDir();

  public abstract boolean isBuildExternalDependencies();

  @Nullable
  public abstract BuildParticipant getBuildParticipant();

  @Nullable
  public abstract UnnamedConfigurable getBuildConfigurable(ModifiableRootModel rootModel);

  public abstract void runValidators(File output, CompileContext context) throws Exception;

  public boolean willBuildExploded() {
    return isExplodedEnabled() && getExplodedPath() != null;
  }

  public String getPresentableName() {
    return getModule().getName();
  }
}