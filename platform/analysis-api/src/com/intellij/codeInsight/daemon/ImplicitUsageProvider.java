// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/// Allows for disabling of highlighting certain elements as unused when such elements are not referenced from the code,
/// but are referenced in some other way, for example:
///
/// - From generated code
/// - From outside containers: `@javax.servlet.annotation.WebServlet public class MyServlet {}`
/// - From some frameworks: `@javax.ejb.EJB private DataStore myInjectedDataStore;`
///
/// Methods in this interface are called during highlighting for every symbol in the file and must be fast.
/// Avoid expensive operations such as reference searches or index queries;
/// prefer using only local information (e.g., checking annotations or modifiers).
public interface ImplicitUsageProvider {
  ExtensionPointName<ImplicitUsageProvider> EP_NAME = new ExtensionPointName<>("com.intellij.implicitUsageProvider");

  /// Returns true if the element should not be reported as unused.
  ///
  /// If detecting implicit usage requires a reference search, consider using [#isReferencedByAlternativeNames(PsiElement)] instead,
  /// which defers the search to the platform's standard unused-detection path.
  boolean isImplicitUsage(@NotNull PsiElement element);

  /// Returns true if the element should not be reported as "assigned but not used".
  boolean isImplicitRead(@NotNull PsiElement element);

  /// Returns true if the element should not be reported as "referenced but never assigned".
  boolean isImplicitWrite(@NotNull PsiElement element);

  /// Returns true if the given element is implicitly initialized to a non-null value.
  default boolean isImplicitlyNotNullInitialized(@NotNull PsiElement element) {
    return false;
  }

  /// Returns true if the given element represents a class (or another data structure declaration, depending on language),
  /// which instances may have implicit initialization steps not directly available in the source code.
  ///
  /// For example, a Java class initializer that is processed via an annotation processor and custom steps are added.
  default boolean isClassWithCustomizedInitialization(@NotNull PsiElement element) {
    return false;
  }

  /// Returns true if the given element may be referenced from code using names different from its declared name.
  ///
  /// When this returns true, the unused symbol detection will not short-circuit based on a text search
  /// for the element's name and will instead perform a full reference search.
  ///
  /// Example: Cucumber step definition methods are referenced from Gherkin files by step text patterns
  /// (e.g., "I am happy") rather than by the Java method name (e.g., "i\_am\_happy").
  ///
  /// See [IDEA-386128](https://youtrack.jetbrains.com/issue/IDEA-386128) for more context.
  default boolean isReferencedByAlternativeNames(@NotNull PsiElement element) {
    return false;
  }
}
