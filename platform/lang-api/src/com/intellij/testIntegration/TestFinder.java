// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.testIntegration;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Triggered when the user uses the "Navigation / Test"  action (= jump to test shortcut). 
 * Corresponding extension point qualified name is {@code com.intellij.testFinder}.
 */
public interface TestFinder {
  ExtensionPointName<TestFinder> EP_NAME = ExtensionPointName.create("com.intellij.testFinder");

  /**
   * Retrieve the source element (PsiFile) to handle some UI elements, like the name displayed in "Choose Test for {file name}".
   *
   * @param from PsiElement where the cursor was when "Navigate to test" was triggered
   * @return the parent PsiFile of the PsiElement where the cursor was when the test finder was invoked
   */
  @Nullable
  PsiElement findSourceElement(@NotNull PsiElement from);

  /**
   * Finds tests for given class.
   *
   * @param element may be of any language but not specific to a current test finder domain language
   * @return found tests for class
   */
  @NotNull
  Collection<PsiElement> findTestsForClass(@NotNull PsiElement element);

  /**
   * Finds classes for given test.
   *
   * @param element may be of any language but not specific to a current test finder domain language
   * @return found classes for test
   */
  @NotNull
  Collection<PsiElement> findClassesForTest(@NotNull PsiElement element);

  boolean isTest(@NotNull PsiElement element);

  /**
   * Checks whether the given test should be navigated to immediately instead of showing it in a popup with only itself and the relevant
   * test creators. This check is only performed when there is exactly 1 test found from {@link #findTestsForClass(PsiElement)}.
   *
   * @param element may be of any language and is not specific to the current test finder's domain language.
   * @return true if the test should be navigated to immediately.
   */
  @ApiStatus.Experimental
  default boolean navigateToTestImmediately(@NotNull PsiElement element) {
    return false;
  }

  /**
   * Returns {@code true} if a test finder can be applied to a particular element, {@code false} otherwise.
   * Other methods should not be called for a test finder that returned {@code false}.
   * It's recommended to override this method together with {@link #getSearchingForTestsForClassProgressTitle(PsiElement)}
   * and {@link #getSearchingForClassesForTestProgressTitle(PsiElement)} as otherwise different implementations
   * might interfere with each other.
   *
   * @param element may be of any language and is not specific to the current test finder's domain language
   * @return {@code true} if a test finder can be applied to a particular element, {@code false} otherwise
   */
  default boolean isApplicable(@NotNull PsiElement element) {
    return true;
  }

  /**
   * Returns a progress title that's shown while {@link #findTestsForClass(PsiElement)} is executed.
   * The result of the method is only used when other implementations return {@code null} or the same result.
   * The default progress title is used otherwise.
   *
   * @param element may be of any language and is not specific to the current test finder's domain language
   * @return localized progress title or {@code null} for default
   * @see #isApplicable(PsiElement)
   */
  default @Nullable @NlsContexts.ProgressTitle String getSearchingForTestsForClassProgressTitle(@NotNull PsiElement element) {
    return null;
  }

  /**
   * Returns a progress title that's shown while {@link #findClassesForTest(PsiElement)} is executed.
   * The result of the method is only used when other implementations return {@code null} or the same result.
   * The default progress title is used otherwise.
   *
   * @param element may be of any language and is not specific to the current test finder's domain language
   * @return localized progress title or {@code null} for default
   * @see #isApplicable(PsiElement)
   */
  default @Nullable @NlsContexts.ProgressTitle String getSearchingForClassesForTestProgressTitle(@NotNull PsiElement element) {
    return null;
  }
}
