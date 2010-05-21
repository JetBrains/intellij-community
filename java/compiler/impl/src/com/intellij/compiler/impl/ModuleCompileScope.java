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

/*
 * @author: Eugene Zhuravlev
 * Date: Jan 20, 2003
 * Time: 5:34:19 PM
 */
package com.intellij.compiler.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ModuleCompileScope extends FileIndexCompileScope {
  private final Project myProject;
  private final Set<Module> myScopeModules;
  private final Module[] myModules;

  public ModuleCompileScope(final Module module, boolean includeDependentModules) {
    myProject = module.getProject();
    myScopeModules = new HashSet<Module>();
    if (includeDependentModules) {
      buildScopeModulesSet(module);
    }
    else {
      myScopeModules.add(module);
    }
    myModules = ModuleManager.getInstance(myProject).getModules();
  }

  public ModuleCompileScope(Project project, final Module[] modules, boolean includeDependentModules) {
    myProject = project;
    myScopeModules = new HashSet<Module>();
    for (Module module : modules) {
      if (module == null) {
        continue; // prevent NPE
      }
      if (includeDependentModules) {
        buildScopeModulesSet(module);
      }
      else {
        myScopeModules.add(module);
      }
    }
    myModules = ModuleManager.getInstance(myProject).getModules();
  }

  private void buildScopeModulesSet(Module module) {
    myScopeModules.add(module);
    final Module[] dependencies = ModuleRootManager.getInstance(module).getDependencies();
    for (Module dependency : dependencies) {
      if (!myScopeModules.contains(dependency)) { // may be in case of module circular dependencies
        buildScopeModulesSet(dependency);
      }
    }
  }

  @NotNull
  public Module[] getAffectedModules() {
    return myScopeModules.toArray(new Module[myScopeModules.size()]);
  }

  protected FileIndex[] getFileIndices() {
    final FileIndex[] indices = new FileIndex[myScopeModules.size()];
    int idx = 0;
    for (final Module myScopeModule : myScopeModules) {
      final Module module = (Module)myScopeModule;
      indices[idx++] = ModuleRootManager.getInstance(module).getFileIndex();
    }
    return indices;
  }

  public boolean belongs(final String url) {
    if (myScopeModules.isEmpty()) {
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
              candidateModule = ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
                public Module compute() {
                  final VirtualFile contentRootFile = VirtualFileManager.getInstance().findFileByUrl(contentRootUrl);
                  if (contentRootFile != null) {
                    return projectFileIndex.getModuleForFile(contentRootFile);
                  }
                  return null;
                }
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

    return false;
  }

  private static boolean isUrlUnderRoot(final String url, final String root) {
    return (url.length() > root.length()) && url.charAt(root.length()) == '/' && FileUtil.startsWith(url, root);
  }

  private final Map<Module, String[]> myContentUrlsCache = new HashMap<Module, String[]>();

  private String[] getModuleContentUrls(final Module module) {
    String[] contentRootUrls = myContentUrlsCache.get(module);
    if (contentRootUrls == null) {
      contentRootUrls = ModuleRootManager.getInstance(module).getContentRootUrls();
      myContentUrlsCache.put(module, contentRootUrls);
    }
    return contentRootUrls;
  }

}
