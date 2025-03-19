// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler;

import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class ModuleSourceSet {
  public enum Type {
    PRODUCTION, TEST, RESOURCES, RESOURCES_TEST;

    public boolean isTest() {
      return this == TEST || this == RESOURCES_TEST;
    }
  }

  private final Module myModule;
  private final Type myType;

  public ModuleSourceSet(@NotNull Module module, @NotNull Type type) {
    myModule = module;
    myType = type;
  }

  public @NotNull Module getModule() {
    return myModule;
  }

  public @NotNull Type getType() {
    return myType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ModuleSourceSet set = (ModuleSourceSet)o;
    return myModule.equals(set.myModule) && myType == set.myType;
  }

  @Override
  public int hashCode() {
    return 31 * myModule.hashCode() + myType.hashCode();
  }

  @Override
  public String toString() {
    return getDisplayName();
  }

  public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getDisplayName() {
    final int choice = myType.isTest()? 1 : 0;
    return JavaCompilerBundle.message("module.sources.set.display.name", choice, myModule.getName());
  }

  public static @NotNull Set<Module> getModules(@NotNull Collection<? extends ModuleSourceSet> sourceSets) {
    return sourceSets.stream().map(ModuleSourceSet::getModule).collect(Collectors.toSet());
  }
}
