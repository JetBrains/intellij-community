// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.testIntegration;

import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * Allows tests generation from production code.
 * Also is used in multiple inspections, intentions, etc. when test framework should be detected.
 */
public interface TestFramework extends PossiblyDumbAware {
  ExtensionPointName<TestFramework> EXTENSION_NAME = ExtensionPointName.create("com.intellij.testFramework");

  /**
   * @return presentable framework name
   */
  @NotNull
  @NlsSafe
  String getName();

  @NotNull
  Icon getIcon();

  /**
   * @return true if module dependencies contain framework library
   */
  boolean isLibraryAttached(@NotNull Module module);

  /**
   * @return path to the library when known (e.g. bundled in the distribution),
   * null otherwise (e.g. when library should be downloaded from maven)
   */
  @Nullable
  String getLibraryPath();

  /**
   * @return FQN of the default superclass for generated test classes, or {@code null} if none
   */
  @Nullable
  String getDefaultSuperClass();

  /**
   * @return {@code true} if the element is a test class recognized by this framework
   */
  boolean isTestClass(@NotNull PsiElement clazz);

  /**
   * When testClass check is slow, {@code true} can be returned under test source root
   */
  boolean isPotentialTestClass(@NotNull PsiElement clazz);

  /**
   * Finds the per-test setup method in the given class.
   * The concrete annotation or naming convention depends on the framework:
   * {@code setUp()} in JUnit 3, {@code @Before} in JUnit 4, {@code @BeforeEach} in JUnit 5, {@code @BeforeMethod} in TestNG.
   *
   * @return the setup method, or {@code null} if not found or framework is not available in scope
   */
  @Nullable
  PsiElement findSetUpMethod(@NotNull PsiElement clazz);

  /**
   * Finds the per-test tear-down method in the given class.
   * The concrete annotation or naming convention depends on the framework:
   * {@code tearDown()} in JUnit 3, {@code @After} in JUnit 4, {@code @AfterEach} in JUnit 5, {@code @AfterMethod} in TestNG.
   *
   * @return the tear-down method, or {@code null} if not found or framework is not available in scope
   */
  @Nullable
  PsiElement findTearDownMethod(@NotNull PsiElement clazz);

  /**
   * Finds the per-test setup method (see {@link #findSetUpMethod}), or creates one using the framework's
   * file template if it does not exist yet.
   *
   * @return the existing or newly created setup method, or {@code null} if the framework is not available in scope
   */
  @Nullable
  PsiElement findOrCreateSetUpMethod(@NotNull PsiElement clazz) throws IncorrectOperationException;

  /**
   * @return file template descriptor used to generate a setUp method
   */
  FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor();

  /**
   * @return file template descriptor used to generate a tearDown method
   */
  FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor();

  /**
   * @return file template descriptor used to generate a test method
   */
  @NotNull
  FileTemplateDescriptor getTestMethodFileTemplateDescriptor();

  /**
   * Finds the class-level setup method that runs once before all tests in the class.
   * Corresponds to {@code @BeforeClass} in JUnit 4 and TestNG, {@code @BeforeAll} in JUnit 5.
   *
   * @return the method, or {@code null} if not found or not supported by this framework
   */
  default @Nullable PsiElement findBeforeClassMethod(@NotNull PsiElement clazz) {
    return null;
  }

  /**
   * @return file template descriptor used to generate the class-level setup method ({@code @BeforeClass} / {@code @BeforeAll}),
   * or {@code null} if not supported by this framework
   */
  default FileTemplateDescriptor getBeforeClassMethodFileTemplateDescriptor() {
    return null;
  }

  /**
   * Finds the class-level tear-down method that runs once after all tests in the class.
   * Corresponds to {@code @AfterClass} in JUnit 4 and TestNG, {@code @AfterAll} in JUnit 5.
   *
   * @return the method, or {@code null} if not found or not supported by this framework
   */
  default @Nullable PsiElement findAfterClassMethod(@NotNull PsiElement clazz) {
    return null;
  }

  /**
   * @return file template descriptor used to generate the class-level tear-down method ({@code @AfterClass} / {@code @AfterAll}),
   * or {@code null} if not supported by this framework
   */
  default FileTemplateDescriptor getAfterClassMethodFileTemplateDescriptor() {
    return null;
  }

  /**
   * Finds the suite-level setup method that runs once before the entire test suite.
   * Corresponds to {@code @BeforeSuite} in JUnit 5 platform suite.
   *
   * @return the method, or {@code null} if not found or not supported by this framework
   */
  default @Nullable PsiElement findBeforeSuiteMethod(@NotNull PsiElement clazz) {
    return null;
  }

  /**
   * Finds the suite-level tear-down method that runs once after the entire test suite.
   * Corresponds to {@code @AfterSuite} in JUnit 5 platform suite.
   *
   * @return the method, or {@code null} if not found or not supported by this framework
   */
  default @Nullable PsiElement findAfterSuiteMethod(@NotNull PsiElement clazz) {
    return null;
  }

  /**
   * should be checked for abstract method error
   */
  boolean isIgnoredMethod(PsiElement element);

  /**
   * should be checked for abstract method error
   */
  boolean isTestMethod(PsiElement element);

  /**
   * @param checkAbstract if {@code false}, abstract methods are also considered test methods
   * @return {@code true} if the element is a test method
   */
  default boolean isTestMethod(PsiElement element, boolean checkAbstract) {
    return isTestMethod(element);
  }

  @NotNull
  Language getLanguage();
}
