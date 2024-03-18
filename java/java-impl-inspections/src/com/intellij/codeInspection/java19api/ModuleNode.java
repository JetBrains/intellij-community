// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19api;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;

import java.util.*;

import static org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE;

class ModuleNode implements Comparable<ModuleNode> {
  @Nullable private final Module myModule;
  @NotNull private final Set<String> myDeclaredPackages;
  @NotNull private final Set<String> myRequiredPackages;
  @NotNull private final Map<ModuleNode, Set<DependencyType>> myDependencies = new TreeMap<>();
  @NotNull private final Set<String> myExports = new TreeSet<>();
  @Nullable private final PsiJavaModule myDescriptor;
  @NotNull private final String myName;

  ModuleNode(@NotNull Module module,
             @NotNull Set<String> declaredPackages,
             @NotNull Set<String> requiredPackages,
             @NotNull UniqueModuleNames uniqueModuleNames) {
    myModule = module;
    myDeclaredPackages = new HashSet<>(declaredPackages);

    myRequiredPackages = new HashSet<>(requiredPackages);
    myRequiredPackages.removeAll(myDeclaredPackages);

    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(myModule);
    myDescriptor = ReadAction.compute(() -> {
      VirtualFile[] sourceRoots = moduleRootManager.getSourceRoots(false);
      return sourceRoots.length != 0 ? findDescriptor(module, sourceRoots[0]) : null;
    });
    myName = ReadAction.compute(() -> myDescriptor != null ? myDescriptor.getName() : uniqueModuleNames.getUniqueName(myModule));
  }

  ModuleNode(@NotNull PsiJavaModule descriptor) {
    myModule = ReadAction.compute(() -> ModuleUtilCore.findModuleForPsiElement(descriptor));
    myDeclaredPackages = Collections.emptySet();
    myRequiredPackages = Collections.emptySet();
    myDescriptor = descriptor;
    myName = ReadAction.compute(() -> myDescriptor.getName());
  }

  @Nullable
  Module getModule() {
    return myModule;
  }

  @NotNull
  Set<String> getDeclaredPackages() {
    return myDeclaredPackages;
  }

  @NotNull
  Set<String> getRequiredPackages() {
    return myRequiredPackages;
  }

  @NotNull
  Map<ModuleNode, Set<DependencyType>> getDependencies() {
    return myDependencies;
  }

  @NotNull
  Set<String> getExports() {
    return myExports;
  }

  void addExport(@NotNull String packageName) {
    myExports.add(packageName);
  }

  @Nullable
  PsiJavaModule getDescriptor() {
    return myDescriptor;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return myName;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof ModuleNode node && getName().equals(node.getName());
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public int compareTo(@NotNull ModuleNode o) {
    int m1 = myModule == null ? 0 : 1;
    int m2 = o.myModule == null ? 0 : 1;
    if (m1 != m2) return m1 - m2;
    int j1 = getName().startsWith("java.") || getName().startsWith("javax.") ? 0 : 1;
    int j2 = o.getName().startsWith("java.") || o.getName().startsWith("javax.") ? 0 : 1;
    if (j1 != j2) return j1 - j2;
    return StringUtil.compare(getName(), o.getName(), false);
  }

  @Nullable
  PsiDirectory getRootDir() {
    if (myModule == null) return null;
    return ReadAction.compute(() -> {
      ModuleRootManager moduleManager = ModuleRootManager.getInstance(myModule);
      List<VirtualFile> folderCandidates = new ArrayList<>();
      for (ContentEntry entry : moduleManager.getContentEntries()) {
        for (SourceFolder folder : entry.getSourceFolders()) {
          if (isSourceFolder(folder)) {
            folderCandidates.add(folder.getFile());
          }
        }
      }

      PsiManager psiManager = PsiManager.getInstance(myModule.getProject());
      return folderCandidates.stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparingInt((VirtualFile vFile) -> "java".equals(vFile.getName()) ? 0 : 1).thenComparing(VirtualFile::getName))
        .map(psiManager::findDirectory)
        .filter(Objects::nonNull).findFirst().orElse(null);
    });
  }

  private static boolean isSourceFolder(@NotNull SourceFolder folder) {
    final JpsModuleSourceRoot sourceRoot = folder.getJpsElement();
    if (sourceRoot.getRootType() != SOURCE) return false;
    if (!(sourceRoot.getProperties() instanceof JavaSourceRootProperties javaProperties)) return false;
    return !javaProperties.isForGeneratedSources();
  }

  @Nullable
  private static PsiJavaModule findDescriptor(@NotNull Module module, @Nullable VirtualFile root) {
    return JavaModuleGraphUtil.findDescriptorByFile(root, module.getProject());
  }

  enum DependencyType {
    STATIC,
    TRANSITIVE
  }
}