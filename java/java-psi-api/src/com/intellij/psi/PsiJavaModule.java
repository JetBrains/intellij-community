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
  /**
   * Checks whether the module should be excluded from automatic resolution when loaded from the classpath.
   * This method reads the module resolution attributes from {@code module-info.class}, as specified in JEP 11.
   *
   * @return {@code true} if the module is marked with {@code DO_NOT_RESOLVE_BY_DEFAULT}, preventing automatic resolution;
   *         {@code false} otherwise.
   * @see <a href="https://openjdk.org/jeps/11">JEP 11: Incubator Modules</a>
   */
  boolean doNotResolveByDefault();
  /**
   * Checks whether the module is marked as deprecated.
   * This method reads the module resolution attributes from {@code module-info.class}, as specified in JEP 11.
   *
   * @return {@code true} if the module is marked with {@code WARN_DEPRECATED}, indicating that it is deprecated;
   *         {@code false} otherwise.
   * @see <a href="https://openjdk.org/jeps/11">JEP 11: Incubator Modules</a>
   */
  boolean warnDeprecated();

  /**
   * Checks whether the module is deprecated and scheduled for removal in a future release.
   * This method reads the module resolution attributes from {@code module-info.class}, as specified in JEP 11.
   *
   * @return {@code true} if the module is marked with {@code WARN_DEPRECATED_FOR_REMOVAL}, indicating that
   *         it is deprecated and will be removed in a future release;
   *         {@code false} otherwise.
   * @see <a href="https://openjdk.org/jeps/11">JEP 11: Incubator Modules</a>
   */
  boolean warnDeprecatedForRemoval();

  /**
   * Checks whether the module is in incubating mode, meaning it is not yet standardized.
   * This method reads the module resolution attributes from {@code module-info.class}, as specified in JEP 11.
   *
   * @return {@code true} if the module is marked with {@code WARN_INCUBATING}, indicating that it is an
   *         incubator module and subject to change in future releases;
   *         {@code false} otherwise.
   * @see <a href="https://openjdk.org/jeps/11">JEP 11: Incubator Modules</a>
   */
  boolean warnIncubating();

  @NotNull Iterable<PsiRequiresStatement> getRequires();
  @NotNull Iterable<PsiPackageAccessibilityStatement> getExports();
  @NotNull Iterable<PsiPackageAccessibilityStatement> getOpens();
  @NotNull Iterable<PsiUsesStatement> getUses();
  @NotNull Iterable<PsiProvidesStatement> getProvides();
}
