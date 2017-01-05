/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.compiler.impl;

import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author nik
 */
public class CompileScopeUtil {
  private static final Key<List<TargetTypeBuildScope>> BASE_SCOPE_FOR_EXTERNAL_BUILD = Key.create("SCOPE_FOR_EXTERNAL_BUILD");

  public static void setBaseScopeForExternalBuild(@NotNull CompileScope scope, @NotNull List<TargetTypeBuildScope> scopes) {
    scope.putUserData(BASE_SCOPE_FOR_EXTERNAL_BUILD, scopes);
  }

  public static void addScopesForModules(Collection<Module> modules, List<TargetTypeBuildScope> scopes, boolean forceBuild) {
    if (!modules.isEmpty()) {
      for (JavaModuleBuildTargetType type : JavaModuleBuildTargetType.ALL_TYPES) {
        TargetTypeBuildScope.Builder builder = TargetTypeBuildScope.newBuilder().setTypeId(type.getTypeId()).setForceBuild(forceBuild);
        for (Module module : modules) {
          builder.addTargetId(module.getName());
        }
        scopes.add(builder.build());
      }
    }
  }

  public static List<TargetTypeBuildScope> getBaseScopeForExternalBuild(@NotNull CompileScope scope) {
    return scope.getUserData(BASE_SCOPE_FOR_EXTERNAL_BUILD);
  }

  public static List<TargetTypeBuildScope> mergeScopes(List<TargetTypeBuildScope> scopes1, List<TargetTypeBuildScope> scopes2) {
    if (scopes2.isEmpty()) return scopes1;
    if (scopes1.isEmpty()) return scopes2;

    Map<String, TargetTypeBuildScope> scopeById = new HashMap<>();
    mergeScopes(scopeById, scopes1);
    mergeScopes(scopeById, scopes2);
    return new ArrayList<>(scopeById.values());
  }

  private static void mergeScopes(Map<String, TargetTypeBuildScope> scopeById, List<TargetTypeBuildScope> scopes) {
    for (TargetTypeBuildScope scope : scopes) {
      String id = scope.getTypeId();
      TargetTypeBuildScope old = scopeById.get(id);
      if (old == null) {
        scopeById.put(id, scope);
      }
      else {
        scopeById.put(id, mergeScope(old, scope));
      }
    }
  }

  private static TargetTypeBuildScope mergeScope(TargetTypeBuildScope scope1, TargetTypeBuildScope scope2) {
    String typeId = scope1.getTypeId();
    if (scope1.getAllTargets()) {
      return !scope1.getForceBuild() && scope2.getForceBuild() ? createAllTargetForcedBuildScope(typeId) : scope1;
    }
    if (scope2.getAllTargets()) {
      return !scope2.getForceBuild() && scope1.getForceBuild() ? createAllTargetForcedBuildScope(typeId) : scope2;
    }
    return TargetTypeBuildScope.newBuilder()
      .setTypeId(typeId)
      .setForceBuild(scope1.getForceBuild() || scope2.getForceBuild())
      .addAllTargetId(scope1.getTargetIdList())
      .addAllTargetId(scope2.getTargetIdList())
      .build();
  }

  private static TargetTypeBuildScope createAllTargetForcedBuildScope(final String typeId) {
    return TargetTypeBuildScope.newBuilder().setTypeId(typeId).setForceBuild(true).setAllTargets(true).build();
  }

  public static boolean allProjectModulesAffected(CompileContextImpl compileContext) {
    final Set<Module> allModules = new HashSet<>(Arrays.asList(compileContext.getProjectCompileScope().getAffectedModules()));
    allModules.removeAll(Arrays.asList(compileContext.getCompileScope().getAffectedModules()));
    return allModules.isEmpty();
  }

  public static List<String> fetchFiles(CompileContextImpl context) {
    if (context.isRebuild()) {
      return Collections.emptyList();
    }
    final CompileScope scope = context.getCompileScope();
    if (shouldFetchFiles(scope)) {
      return Arrays.stream(scope.getFiles(null, true)).map(VirtualFile::getPath).collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  private static boolean shouldFetchFiles(CompileScope scope) {
    if (scope instanceof CompositeScope) {
      for (CompileScope compileScope : ((CompositeScope)scope).getScopes()) {
        if (shouldFetchFiles(compileScope)) {
          return true;
        }
      }
    }
    return scope instanceof OneProjectItemCompileScope || scope instanceof FileSetCompileScope;
  }
}
