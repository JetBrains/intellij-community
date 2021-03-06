// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Represents a Java module declaration.
 */
public interface PsiJavaModule extends NavigatablePsiElement, PsiNameIdentifierOwner, PsiModifierListOwner, PsiJavaDocumentedElement {
  String MODULE_INFO_CLASS = "module-info";
  @NlsSafe String MODULE_INFO_FILE = MODULE_INFO_CLASS + ".java";
  String MODULE_INFO_CLS_FILE = MODULE_INFO_CLASS + ".class";
  String JAVA_BASE = "java.base";
  String AUTO_MODULE_NAME = "Automatic-Module-Name";

  /* See http://openjdk.java.net/jeps/261#Class-loaders, "Class loaders" */
  Set<String> UPGRADEABLE = ContainerUtil.immutableSet(
    "java.activation", "java.compiler", "java.corba", "java.transaction", "java.xml.bind", "java.xml.ws", "java.xml.ws.annotation",
    "jdk.internal.vm.compiler", "jdk.xml.bind", "jdk.xml.ws");

  @Override @NotNull PsiJavaModuleReferenceElement getNameIdentifier();
  @Override @NotNull String getName();

  @NotNull Iterable<PsiRequiresStatement> getRequires();
  @NotNull Iterable<PsiPackageAccessibilityStatement> getExports();
  @NotNull Iterable<PsiPackageAccessibilityStatement> getOpens();
  @NotNull Iterable<PsiUsesStatement> getUses();
  @NotNull Iterable<PsiProvidesStatement> getProvides();
}