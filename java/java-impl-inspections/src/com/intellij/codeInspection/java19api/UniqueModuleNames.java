// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19api;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.java.stubs.index.JavaModuleNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.SlowOperations;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class UniqueModuleNames {
  private final UniqueNameGenerator myNameGenerator;

  UniqueModuleNames(@NotNull Project project) {
    final JavaModuleNameIndex index = JavaModuleNameIndex.getInstance();
    final GlobalSearchScope scope = ProjectScope.getAllScope(project);

    final List<PsiJavaModule> modules = new ArrayList<>();
    ReadAction.run(() -> {
      for (String key : index.getAllKeys(project)) {
        modules.addAll(index.getModules(key, project, scope));
      }
    });
    myNameGenerator = new UniqueNameGenerator(modules, module -> ReadAction.compute(() -> module.getName()));
  }

  @NotNull
  String getUniqueName(@NotNull Module module) {
    final String name = NameConverterUtil.convertModuleName(module.getName());
    return myNameGenerator.generateUniqueName(name);
  }
}