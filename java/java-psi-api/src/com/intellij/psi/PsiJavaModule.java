// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a Java module declaration.
 */
public interface PsiJavaModule extends NavigatablePsiElement, PsiNameIdentifierOwner, PsiModifierListOwner, PsiJavaDocumentedElement {
  String MODULE_INFO_CLASS = "module-info";
  @NlsSafe String MODULE_INFO_FILE = MODULE_INFO_CLASS + ".java";
  String MODULE_INFO_CLS_FILE = MODULE_INFO_CLASS + ".class";

  /**
   * "java.base" module is the the core module of the Java SE Platform.
   * As such, it does not require explicit declaration in the module's {@code requires} directive.
   */
  String JAVA_BASE = "java.base";

  /**
   * Represents the manifest attribute name used to specify an automatic module name.
   * When a JAR file does not contain a module-info.java file,
   * it can still be used as an automatic module by providing this attribute in its manifest.
   * The value of this attribute defines the module's name, allowing it to be required by other modules.
   */
  String AUTO_MODULE_NAME = "Automatic-Module-Name";

  /**
   * When a service provider specifies a "provider" method, the service loader uses this method to create a service provider instance.
   * This method must be public, static, have no parameters, and return a type compatible with the service's interface or class.
   */
  String PROVIDER_METHOD = "provider";

  /* See http://openjdk.org/jeps/261#Class-loaders, "Class loaders" */
  Set<String> UPGRADEABLE = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
    "java.activation", "java.compiler", "java.corba", "java.transaction", "java.xml.bind", "java.xml.ws", "java.xml.ws.annotation",
    "jdk.internal.vm.compiler", "jdk.xml.bind", "jdk.xml.ws")));

  @Override @NotNull PsiJavaModuleReferenceElement getNameIdentifier();
  @Override @NotNull String getName();
  boolean doNotResolveByDefault();
  boolean warnDeprecated();
  boolean warnDeprecatedForRemoval();
  boolean warnIncubating();

  @NotNull Iterable<PsiRequiresStatement> getRequires();
  @NotNull Iterable<PsiPackageAccessibilityStatement> getExports();
  @NotNull Iterable<PsiPackageAccessibilityStatement> getOpens();
  @NotNull Iterable<PsiUsesStatement> getUses();
  @NotNull Iterable<PsiProvidesStatement> getProvides();
}
