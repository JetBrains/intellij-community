// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.testIntegration;

import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Allows tests generation from production code.
 * 
 * Also is used in multiple inspections, intentions, etc. when test framework should be detected. 
 */
public interface TestFramework {
  ExtensionPointName<TestFramework> EXTENSION_NAME = ExtensionPointName.create("com.intellij.testFramework");

  /**
   * @return presentable framework name
   */
  @NotNull @NlsSafe
  String getName();

  @NotNull
  Icon getIcon();

  /**
   * @return true if module dependencies contain framework library 
   */
  boolean isLibraryAttached(@NotNull Module module);

  /**
   * @return path to the library when known (e.g. bundled in the distribution),
   *         null otherwise (e.g. when library should be downloaded from maven)
   */
  @Nullable
  String getLibraryPath();

  @Nullable
  String getDefaultSuperClass();

  boolean isTestClass(@NotNull PsiElement clazz);

  /**
   * When testClass check is slow, {@code true} can be returned under test source root
   */
  boolean isPotentialTestClass(@NotNull PsiElement clazz);

  @Nullable
  PsiElement findSetUpMethod(@NotNull PsiElement clazz);

  @Nullable
  PsiElement findTearDownMethod(@NotNull PsiElement clazz);

  @Nullable
  PsiElement findOrCreateSetUpMethod(@NotNull PsiElement clazz) throws IncorrectOperationException;

  FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor();

  FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor();

  @NotNull
  FileTemplateDescriptor getTestMethodFileTemplateDescriptor();

  default @Nullable PsiElement findBeforeClassMethod(@NotNull PsiElement clazz) {
    return null;
  }

  default FileTemplateDescriptor getBeforeClassMethodFileTemplateDescriptor() {
    return null;
  }

  default @Nullable PsiElement findAfterClassMethod(@NotNull PsiElement clazz) {
    return null;
  }

  default FileTemplateDescriptor getAfterClassMethodFileTemplateDescriptor() {
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
  
  default boolean isTestMethod(PsiElement element, boolean checkAbstract) {
    return isTestMethod(element);
  }

  @NotNull
  Language getLanguage();
}
