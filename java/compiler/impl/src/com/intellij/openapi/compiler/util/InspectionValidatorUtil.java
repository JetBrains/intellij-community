// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.compiler.util;

import com.intellij.compiler.impl.FileSetCompileScope;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.descriptors.ConfigFile;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author peter
 */
public final class InspectionValidatorUtil {
  private InspectionValidatorUtil() {
  }

  public static void addDescriptor(@NotNull final Collection<? super VirtualFile> result, @Nullable final ConfigFile configFile) {
    if (configFile != null) {
      ContainerUtil.addIfNotNull(result, configFile.getVirtualFile());
    }
  }

  public static void addFile(@NotNull final Collection<? super VirtualFile> result, @Nullable final PsiFile psiFile) {
    if (psiFile != null) {
      ContainerUtil.addIfNotNull(result, psiFile.getVirtualFile());
    }
  }


  public static Collection<VirtualFile> expandCompileScopeIfNeeded(final Collection<VirtualFile> result, final CompileContext context) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(context.getProject()).getFileIndex();
    final THashSet<VirtualFile> set = new THashSet<>();
    final THashSet<Module> modules = new THashSet<>();
    for (VirtualFile file : result) {
      if (index.getSourceRootForFile(file) == null) {
        set.add(file);
        ContainerUtil.addIfNotNull(modules, index.getModuleForFile(file));
      }
    }
    if (!set.isEmpty()) {
      ((CompileContextEx)context).addScope(new FileSetCompileScope(set, modules.toArray(new Module[modules.size()])));
    }
    return result;
  }
}
