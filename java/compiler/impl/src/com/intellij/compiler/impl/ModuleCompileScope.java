// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.compiler.impl;

import com.intellij.compiler.ModuleSourceSet;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class ModuleCompileScope extends FileIndexCompileScope {
  private final Project myProject;
  private final Set<Module> myTestSourcesModules;
  private final Set<Module> myScopeModules;
  private final Module[] myModules;
  private final Collection<String> myIncludedUnloadedModules;
  private final boolean myIncludeTests;

  public ModuleCompileScope(final Module module, boolean includeDependentModules) {
    this(module.getProject(), Collections.singleton(module), Collections.emptyList(), includeDependentModules, false);
  }

  public ModuleCompileScope(Project project, final Module[] modules, boolean includeDependentModules) {
    this(project, modules, includeDependentModules, false);
  }

  public ModuleCompileScope(Project project, final Module[] modules, boolean includeDependentModules, boolean includeRuntimeDependencies) {
    this(project, Arrays.asList(modules), Collections.emptyList(), includeDependentModules, includeRuntimeDependencies);
  }

  public ModuleCompileScope(Project project, final Collection<? extends Module> modules, Collection<String> includedUnloadedModules, boolean includeDependentModules, boolean includeRuntimeDeps) {
    this(project, modules, includedUnloadedModules, includeDependentModules, includeRuntimeDeps, true);
  }
  
  public ModuleCompileScope(Project project, final Collection<? extends Module> modules, Collection<String> includedUnloadedModules, boolean includeDependentModules, boolean includeRuntimeDeps, boolean includeTests) {
    myProject = project;
    myIncludedUnloadedModules = includedUnloadedModules;
    myIncludeTests = includeTests;
    myTestSourcesModules = new HashSet<>();
    myScopeModules = new HashSet<>();
    for (Module module : modules) {
      if (module == null) {
        continue; // prevent NPE
      }
      if (includeTests) {
        myTestSourcesModules.add(module);
      }
      if (includeDependentModules) {
        OrderEnumerator enumerator = ModuleRootManager.getInstance(module).orderEntries().recursively();
        if (!includeRuntimeDeps) {
          enumerator = enumerator.compileOnly();
        }
        boolean collectTestModules = includeTests && shouldIncludeTestsFromDependentModulesToTestClasspath(module);
        enumerator.forEachModule(m -> {
          myScopeModules.add(m);
          if (collectTestModules) {
            myTestSourcesModules.add(m);
          }
          return true;
        });
      }
      else {
        myScopeModules.add(module);
      }
    }
    myModules = ModuleManager.getInstance(myProject).getModules();
  }

  @Override
  public Module @NotNull [] getAffectedModules() {
    return myScopeModules.toArray(Module.EMPTY_ARRAY);
  }

  @Override
  public Collection<ModuleSourceSet> getAffectedSourceSets() {
    Collection<ModuleSourceSet> result = super.getAffectedSourceSets();
    if (myIncludeTests) {
      return result.stream().filter(set -> !set.getType().isTest() || myTestSourcesModules.contains(set.getModule())).collect(Collectors.toList());
    }
    return result.stream().filter(set -> !set.getType().isTest()).collect(Collectors.toList());
  }

  public static boolean shouldIncludeTestsFromDependentModulesToTestClasspath(@NotNull Module module) {
    for (OrderEnumerationHandler.Factory factory : OrderEnumerationHandler.EP_NAME.getExtensionList()) {
      if (factory.isApplicable(module) && !factory.createHandler(module).shouldIncludeTestsFromDependentModulesToTestClasspath()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public @NotNull Collection<String> getAffectedUnloadedModules() {
    return Collections.unmodifiableCollection(myIncludedUnloadedModules);
  }

  @Override
  protected FileIndex[] getFileIndices() {
    final FileIndex[] indices = new FileIndex[myScopeModules.size()];
    int idx = 0;
    for (final Module module : myScopeModules) {
      indices[idx++] = ModuleRootManager.getInstance(module).getFileIndex();
    }
    return indices;
  }

  @Override
  public boolean belongs(final @NotNull String url) {
    if (myScopeModules.isEmpty() && myIncludedUnloadedModules.isEmpty()) {
      return false; // optimization
    }
    Module candidateModule = null;
    int maxUrlLength = 0;
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    for (final Module module : myModules) {
      final String[] contentRootUrls = getModuleContentUrls(module);
      for (final String contentRootUrl : contentRootUrls) {
        if (contentRootUrl.length() < maxUrlLength) {
          continue;
        }
        if (!isUrlUnderRoot(url, contentRootUrl)) {
          continue;
        }
        if (contentRootUrl.length() == maxUrlLength) {
          if (candidateModule == null) {
            candidateModule = module;
          }
          else {
            // the same content root exists in several modules
            if (!candidateModule.equals(module)) {
              candidateModule = ReadAction.compute(() -> {
                final VirtualFile contentRootFile = VirtualFileManager.getInstance().findFileByUrl(contentRootUrl);
                if (contentRootFile != null) {
                  return projectFileIndex.getModuleForFile(contentRootFile);
                }
                return null;
              });
            }
          }
        }
        else {
          maxUrlLength = contentRootUrl.length();
          candidateModule = module;
        }
      }
    }

    if (candidateModule != null && myScopeModules.contains(candidateModule)) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(candidateModule);
      final String[] excludeRootUrls = moduleRootManager.getExcludeRootUrls();
      for (String excludeRootUrl : excludeRootUrls) {
        if (isUrlUnderRoot(url, excludeRootUrl)) {
          return false;
        }
      }
      final String[] sourceRootUrls = moduleRootManager.getSourceRootUrls();
      for (String sourceRootUrl : sourceRootUrls) {
        if (isUrlUnderRoot(url, sourceRootUrl)) {
          return true;
        }
      }
    }

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (String unloadedModule : myIncludedUnloadedModules) {
      UnloadedModuleDescription moduleDescription = moduleManager.getUnloadedModuleDescription(unloadedModule);
      if (moduleDescription != null) {
        for (VirtualFilePointer pointer : moduleDescription.getContentRoots()) {
          if (isUrlUnderRoot(url, pointer.getUrl())) {
            return true;
          }
        }
      }
    }

    return false;
  }

  private static boolean isUrlUnderRoot(final String url, final String root) {
    return (url.length() > root.length()) && url.charAt(root.length()) == '/' && FileUtil.startsWith(url, root);
  }

  private final Map<Module, String[]> myContentUrlsCache = new HashMap<>();

  private String[] getModuleContentUrls(final Module module) {
    String[] contentRootUrls = myContentUrlsCache.get(module);
    if (contentRootUrls == null) {
      contentRootUrls = ModuleRootManager.getInstance(module).getContentRootUrls();
      myContentUrlsCache.put(module, contentRootUrls);
    }
    return contentRootUrls;
  }

}
