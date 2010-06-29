/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathsList;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class OrderEnumerator {
  public abstract OrderEnumerator productionOnly();

  public abstract OrderEnumerator compileOnly();
  public abstract OrderEnumerator runtimeOnly();


  public abstract OrderEnumerator withoutSdk();
  public abstract OrderEnumerator withoutLibraries();
  public abstract OrderEnumerator withoutDepModules();
  public abstract OrderEnumerator withoutModuleSourceEntries();

  public abstract OrderEnumerator recursively();
  public abstract OrderEnumerator exportedOnly();

  public abstract OrderEnumerator satisfying(Condition<OrderEntry> condition);


  public abstract Collection<VirtualFile> getClassesRoots();
  public abstract Collection<VirtualFile> getSourceRoots();

  public abstract PathsList getPathsList();
  public abstract void collectPaths(PathsList list);
  public abstract PathsList getSourcePathsList();
  public abstract void collectSourcePaths(PathsList list);

  public abstract void forEach(Processor<OrderEntry> processor);
  public abstract void forEachLibrary(Processor<Library> processor);

  public static OrderEnumerator orderEntries(@NotNull Module module) {
    return ModuleRootManager.getInstance(module).orderEntries();
  }

  public static OrderEnumerator orderEntries(@NotNull Project project) {
    return ProjectRootManager.getInstance(project).orderEntries();
  }
}
