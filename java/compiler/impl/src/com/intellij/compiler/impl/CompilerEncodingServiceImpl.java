// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.impl;

import com.intellij.compiler.CompilerEncodingService;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.nio.charset.Charset;
import java.util.*;

public final class CompilerEncodingServiceImpl extends CompilerEncodingService {
  @NotNull private final Project myProject;
  private final CachedValue<Map<Module, Set<Charset>>> myModuleFileEncodings;

  public CompilerEncodingServiceImpl(@NotNull Project project) {
    myProject = project;
    myModuleFileEncodings = CachedValuesManager.getManager(project).createCachedValue(() -> {
      Map<Module, Set<Charset>> result = computeModuleCharsetMap();
      return CachedValueProvider.Result.create(result, ProjectRootManager.getInstance(myProject),
                                               ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(myProject)).getModificationTracker());
    }, false);
  }

  @NotNull
  private Map<Module, Set<Charset>> computeModuleCharsetMap() {
    final Map<Module, Set<Charset>> map = new HashMap<>();
    final Map<? extends VirtualFile, ? extends Charset> mappings = ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(myProject)).getAllMappings();
    ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    for (Map.Entry<? extends VirtualFile, ? extends Charset> entry : mappings.entrySet()) {
      final VirtualFile file = entry.getKey();
      final Charset charset = entry.getValue();
      if (file == null || charset == null || (!file.isDirectory() && !compilerManager.isCompilableFileType(file.getFileType()))
          || !index.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES)) continue;

      final Module module = index.getModuleForFile(file);
      if (module == null) continue;

      Set<Charset> set = map.get(module);
      if (set == null) {
        set = new LinkedHashSet<>();
        map.put(module, set);

        final VirtualFile sourceRoot = index.getSourceRootForFile(file);
        VirtualFile current = file.getParent();
        Charset parentCharset = null;
        while (current != null) {
          final Charset currentCharset = mappings.get(current);
          if (currentCharset != null) {
            parentCharset = currentCharset;
          }
          if (current.equals(sourceRoot)) {
            break;
          }
          current = current.getParent();
        }
        if (parentCharset != null) {
          set.add(parentCharset);
        }
      }
      set.add(charset);
    }
    //todo perhaps we should take into account encodings of source roots only not individual files
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      for (VirtualFile file : ModuleRootManager.getInstance(module).getSourceRoots(true)) {
        Charset encoding = EncodingProjectManager.getInstance(myProject).getEncoding(file, true);
        if (encoding != null) {
          Set<Charset> charsets = map.get(module);
          if (charsets == null) {
            charsets = new LinkedHashSet<>();
            map.put(module, charsets);
          }
          charsets.add(encoding);
        }
      }
    }

    return map;
  }

  @Override
  @Nullable
  public Charset getPreferredModuleEncoding(@NotNull Module module) {
    final Set<Charset> encodings = myModuleFileEncodings.getValue().get(module);
    return ContainerUtil.getFirstItem(encodings, EncodingProjectManager.getInstance(myProject).getDefaultCharset());
  }

  @NotNull
  @Override
  public Collection<Charset> getAllModuleEncodings(@NotNull Module module) {
    final Set<Charset> encodings = myModuleFileEncodings.getValue().get(module);
    if (encodings != null) {
      return encodings;
    }
    return ContainerUtil.createMaybeSingletonList(EncodingProjectManager.getInstance(myProject).getDefaultCharset());
  }
}
