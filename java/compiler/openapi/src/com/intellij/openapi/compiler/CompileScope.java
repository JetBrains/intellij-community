// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.compiler;

import com.intellij.compiler.ModuleSourceSet;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Interface describing the current compilation scope.
 * Only sources that belong to the scope are compiled.
 *
 * @see CompilerManager#compile(CompileScope, CompileStatusNotification)
 */
public interface CompileScope extends ExportableUserDataHolder {
  CompileScope[] EMPTY_ARRAY = new CompileScope[0];
  /**
   * Returns the list of files within the scope.
   *
   * @param fileType     the type of the files. Null should be passed if all available files are needed.
   * @param inSourceOnly if true, files are searched only in directories within the scope that are marked as "sources" or "test sources" in module settings.
   *                     Otherwise, files are searched in all directories that belong to the scope.
   * @return an array of files of the given type that belong to this scope.
   */
  VirtualFile @NotNull [] getFiles(@Nullable FileType fileType, boolean inSourceOnly);

  /**
   * Checks if the file with the specified URL belongs to the scope.
   *
   * @param url a VFS url. Note that the actual file may not exist on the disk.
   * @return true, if the url specified belongs to the scope, false otherwise.
   *         Note: the method may be time-consuming.
   */
  boolean belongs(@NotNull String url);

  /**
   * Returns the list of module files in which belong to the scope.
   *
   * @return an array of modules this scope affects.
   */
  Module @NotNull [] getAffectedModules();

  /**
   * @return list of names of unloaded modules this scope affects.
   */
  default @NotNull Collection<String> getAffectedUnloadedModules() {
    return Collections.emptyList();
  }

  /**
   * @return similar to {@link #getAffectedModules}, but is more precise about which kinds of source roots are affected: production and/or tests
   */
  default Collection<ModuleSourceSet> getAffectedSourceSets() {
    List<ModuleSourceSet> sets = new SmartList<>();
    for (Module module : getAffectedModules()) {
      for (ModuleSourceSet.Type setType : ModuleSourceSet.Type.values()) {
        sets.add(new ModuleSourceSet(module, setType));
      }
    }
    return sets;
  }
}
