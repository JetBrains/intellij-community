// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19api;

import com.intellij.java.analysis.impl.bytecode.AbstractDependencyVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

class ModuleVisitor extends AbstractDependencyVisitor {
  private final Set<String> myRequiredPackages = new HashSet<>();
  private final Set<String> myDeclaredPackages = new HashSet<>();
  private final Function<String, String> myCheckPackage;

  ModuleVisitor(@NotNull Function<String, String> checkPackage) {
    myCheckPackage = checkPackage;
  }

  @Override
  protected void addClassName(String className) {
    final String packageName = myCheckPackage.apply(className);
    if (packageName != null) {
      myRequiredPackages.add(packageName);
    }
  }

  @Override
  public void visit(int version,
                    int access,
                    @NotNull String name,
                    @Nullable String signature,
                    @Nullable String superName,
                    String @NotNull [] interfaces) {
    super.visit(version, access, name, signature, superName, interfaces);

    String packageName = myCheckPackage.apply(getCurrentClassName());
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
