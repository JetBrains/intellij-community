// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.java.codeserver.core.JavaManifestUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.java.stubs.index.JavaAutoModuleNameIndex;
import com.intellij.psi.impl.java.stubs.index.JavaModuleNameIndex;
import com.intellij.psi.impl.java.stubs.index.JavaSourceModuleNameIndex;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.JavaModuleSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarFile;

public final class JavaModuleSearcher implements QueryExecutor<PsiJavaModule, JavaModuleSearch.Parameters> {
  @Override
  public boolean execute(JavaModuleSearch.@NotNull Parameters queryParameters,
                         @NotNull Processor<? super PsiJavaModule> consumer) {
    String moduleName = queryParameters.getName();
    Project project = queryParameters.getProject();
    GlobalSearchScope scope = queryParameters.getScope();

    if (moduleName == null) {
      return processAllModules(project, consumer);
    }

    return processModuleByName(moduleName, project, scope, consumer);
  }

  private static boolean processAllModules(@NotNull Project project,
                                           @NotNull Processor<? super PsiJavaModule> consumer) {
    GlobalSearchScope indexScope = GlobalSearchScope.allScope(project);

    // collect all module-name keys
    Set<String> allNames = new LinkedHashSet<>();
    allNames.addAll(JavaModuleNameIndex.getInstance().getAllKeys(project));
    allNames.addAll(JavaSourceModuleNameIndex.getAllKeys(project));
    allNames.addAll(JavaAutoModuleNameIndex.getAllKeys(project));

    Set<String> namesWithResults = new HashSet<>();
    // process real and indexed light modules only.
    for (String name : allNames) {
      if (!processModulesFromIndices(name, project, indexScope, consumer, namesWithResults)) {
        return false;
      }
    }

    return processJpsModules(project, consumer, namesWithResults, null);
  }

  private static boolean processModuleByName(@NotNull String moduleName,
                                             @NotNull Project project,
                                             @NotNull GlobalSearchScope scope,
                                             @NotNull Processor<? super PsiJavaModule> consumer) {
    Set<String> namesWithResults = new HashSet<>();

    if (!processModulesFromIndices(moduleName, project, scope, consumer, namesWithResults)) {
      return false;
    }

    // If we already found the module, no need to fallback.
    if (namesWithResults.contains(moduleName)) {
      return true;
    }

    return processJpsModules(project, consumer, namesWithResults, moduleName);
  }

  private static boolean processJpsModules(@NotNull Project project,
                                           @NotNull Processor<? super PsiJavaModule> consumer,
                                           @NotNull Set<? super String> namesWithResults,
                                           @Nullable String moduleName) {
    PsiManager psiManager = PsiManager.getInstance(project);
    CachedValuesManager valuesManager = CachedValuesManager.getManager(project);
    ProjectRootModificationTracker tracker = ProjectRootModificationTracker.getInstance(project);
    Module[] modules = ModuleManager.getInstance(project).getModules();

    for (Module module : modules) {
      VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(false);
      if (sourceRoots.length == 0) continue;

      VirtualFile root = sourceRoots[0];

      // auto module name from manifest (including virtual manifests)
      String autoModuleName = JavaManifestUtil.getManifestAttributeValue(module, PsiJavaModule.AUTO_MODULE_NAME);
      if (autoModuleName != null && !namesWithResults.contains(autoModuleName)) {
        if (moduleName != null && moduleName.equals(autoModuleName)) {
          namesWithResults.add(autoModuleName);
          if (!consumer.process(LightJavaModule.create(psiManager, root, autoModuleName))) return false;
          return true;
        }
        else if (moduleName == null) {
          namesWithResults.add(autoModuleName);
          if (!consumer.process(LightJavaModule.create(psiManager, root, autoModuleName))) return false;
          continue;
        }
      }

      // default module name derived from module name
      String defaultModuleName = valuesManager.getCachedValue(module, () ->
        CachedValueProvider.Result.create(LightJavaModule.moduleName(module.getName()), tracker));
      if (!namesWithResults.contains(defaultModuleName)) {
        if (moduleName != null && moduleName.equals(defaultModuleName)) {
          namesWithResults.add(defaultModuleName);
          if (!consumer.process(LightJavaModule.create(psiManager, root, defaultModuleName))) return false;
          return true;
        }
        else if (moduleName == null) {
          namesWithResults.add(defaultModuleName);
          if (!consumer.process(LightJavaModule.create(psiManager, root, defaultModuleName))) return false;
        }
      }
    }
    return true;
  }

  private static boolean processModulesFromIndices(@NotNull String moduleName,
                                                   @NotNull Project project,
                                                   @NotNull GlobalSearchScope scope,
                                                   @NotNull Processor<? super PsiJavaModule> consumer,
                                                   @NotNull Set<? super String> namesWithResults) {
    PsiManager psiManager = PsiManager.getInstance(project);
    // Real modules from module-info.java
    for (PsiJavaModule module : JavaModuleNameIndex.getInstance().getModules(moduleName, project, scope)) {
      namesWithResults.add(moduleName);
      if (!consumer.process(module)) return false;
    }

    // Light modules created from source manifests
    Set<VirtualFile> shadowedRoots = new HashSet<>();
    for (VirtualFile manifest : JavaSourceModuleNameIndex.getFilesByKey(moduleName, scope)) {
      VirtualFile root = getSourceRootFromManifest(manifest);
      if (root == null) continue;

      namesWithResults.add(moduleName);
      shadowedRoots.add(root);

      if (!consumer.process(LightJavaModule.create(psiManager, root, moduleName))) return false;
    }

    // Light modules created from auto-module-name (jar roots)
    for (VirtualFile root : JavaAutoModuleNameIndex.getFilesByKey(moduleName, scope)) {
      if (shadowedRoots.contains(root)) continue;

      VirtualFile manifest = root.findFileByRelativePath(JarFile.MANIFEST_NAME);
      // If the manifest claims a module name (possibly different), skip this root.
      if (manifest != null && LightJavaModule.claimedModuleName(manifest) != null) continue;

      namesWithResults.add(moduleName);
      if (!consumer.process(LightJavaModule.create(psiManager, root, moduleName))) return false;
    }
    return true;
  }

  private static @Nullable VirtualFile getSourceRootFromManifest(@NotNull VirtualFile manifest) {
    VirtualFile parent = manifest.getParent();
    if (parent == null) return null;
    VirtualFile root = parent.getParent();
    if (root == null) return null;
    return root;
  }
}