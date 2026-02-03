// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19api;

import com.intellij.java.analysis.bytecode.JvmBytecodeDeclarationProcessor;
import com.intellij.java.analysis.bytecode.JvmBytecodeReferenceProcessor;
import com.intellij.java.analysis.bytecode.JvmClassBytecodeDeclaration;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

class ModuleVisitor implements JvmBytecodeDeclarationProcessor, JvmBytecodeReferenceProcessor {
  private final Set<String> myRequiredPackages = new HashSet<>();
  private final Set<String> myDeclaredPackages = new HashSet<>();
  private final Function<String, String> myCheckPackage;

  ModuleVisitor(@NotNull Function<String, String> checkPackage) {
    myCheckPackage = checkPackage;
  }

  @Override
  public void processClassReference(@NotNull JvmClassBytecodeDeclaration targetClass, @NotNull JvmClassBytecodeDeclaration sourceClass) {
    String className = targetClass.getTopLevelSourceClassName();
    final String packageName = myCheckPackage.apply(className);
    if (packageName != null) {
      myRequiredPackages.add(packageName);
    }
  }

  @Override
  public void processClass(@NotNull JvmClassBytecodeDeclaration jvmClass) {
    String packageName = myCheckPackage.apply(jvmClass.getTopLevelSourceClassName());
    if (packageName != null) {
      myDeclaredPackages.add(packageName);
    }
  }

  Set<String> getRequiredPackages() {
    return myRequiredPackages;
  }

  Set<String> getDeclaredPackages() {
    return myDeclaredPackages;
  }
}
