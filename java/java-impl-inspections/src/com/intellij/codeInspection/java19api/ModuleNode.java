// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19api;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiManager;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class ModuleNode implements Comparable<ModuleNode> {
  private final Module myModule;
  private final Set<String> myDeclaredPackages;
  private final Set<String> myRequiredPackages;
  private final Set<ModuleNode> myDependencies = new TreeSet<>();
  private final Set<String> myExports = new TreeSet<>();
  private final PsiJavaModule myDescriptor;
  private final String myName;

  ModuleNode(@NotNull Module module,
             @NotNull Set<String> declaredPackages,
             @NotNull Set<String> requiredPackages,
             @NotNull UniqueModuleNames uniqueModuleNames) {
    myModule = module;
    myDeclaredPackages = declaredPackages;
    myRequiredPackages = requiredPackages;

    myDescriptor = ReadAction.compute(() -> {
      VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(false);
      return sourceRoots.length != 0 ? findDescriptor(module, sourceRoots[0]) : null;
    });
    myName = myDescriptor != null ? myDescriptor.getName() : uniqueModuleNames.getUniqueName(myModule);
  }

  ModuleNode(@NotNull PsiJavaModule descriptor) {
    myModule = ReadAction.compute(() -> ModuleUtilCore.findModuleForPsiElement(descriptor));
    myDeclaredPackages = Collections.emptySet();
    myRequiredPackages = Collections.emptySet();
    myDescriptor = descriptor;
    myName = myDescriptor.getName();
  }

  Set<String> getDeclaredPackages() {
    return myDeclaredPackages;
  }

  Set<String> getRequiredPackages() {
    return myRequiredPackages;
  }

  Set<ModuleNode> getDependencies() {
    return myDependencies;
  }

  Set<String> getExports() {
    return myExports;
  }

  void addExport(String packageName) {
    myExports.add(packageName);
  }


  PsiJavaModule getDescriptor() {
    return myDescriptor;
  }

  @NotNull
  String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return myName;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof ModuleNode && myName.equals(((ModuleNode)o).myName);
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public int compareTo(@NotNull ModuleNode o) {
    int m1 = myModule == null ? 0 : 1, m2 = o.myModule == null ? 0 : 1;
    if (m1 != m2) return m1 - m2;
    int j1 = myName.startsWith("java.") || myName.startsWith("javax.") ? 0 : 1;
    int j2 = o.myName.startsWith("java.") || o.myName.startsWith("javax.") ? 0 : 1;
    if (j1 != j2) return j1 - j2;
    return StringUtil.compare(myName, o.myName, false);
  }

  PsiDirectory getRootDir() {
    if (myModule == null) return null;
    return ReadAction.compute(() -> {
      ModuleRootManager moduleManager = ModuleRootManager.getInstance(myModule);
      PsiManager psiManager = PsiManager.getInstance(myModule.getProject());
      return findJavaDirectory(psiManager, moduleManager.getSourceRoots(false));
    });
  }

  @Nullable
  private static PsiDirectory findJavaDirectory(PsiManager psiManager, VirtualFile[] roots) {
    return StreamEx.of(roots)
      .sorted(Comparator.comparingInt((VirtualFile vFile) -> "java".equals(vFile.getName()) ? 0 : 1).thenComparing(VirtualFile::getName))
      .map(psiManager::findDirectory).nonNull().findFirst().orElse(null);
  }

  @Nullable
  private static PsiJavaModule findDescriptor(@NotNull Module module, VirtualFile root) {
    return JavaModuleGraphUtil.findDescriptorByFile(root, module.getProject());
  }
}