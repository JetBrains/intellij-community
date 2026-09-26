// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows detecting a library by unresolved class/package name in the editor.
 * <p>
 * If an external library is detected, e.g., junit or JB annotations library,
 * {@link com.intellij.codeInsight.daemon.impl.quickfix.AddExtLibraryDependencyFix} is registered to configure that missed library
 */
public abstract class ExternalLibraryResolver {
  public static final ExtensionPointName<ExternalLibraryResolver> EP_NAME = ExtensionPointName.create("com.intellij.codeInsight.externalLibraryResolver");

  /**
   * Detects if short name may belong to the library's class
   * 
   * @param isAnnotation {@code ThreeState.YES} if reference is inside annotation,
   *                     {@code ThreeState.UNSURE} in imports, 
   *                     {@code ThreeState.NO} otherwise
   * @param contextModule can be used if e.g., different versions must be used based on the language level of the module or similar
   * @return library descriptor if {@code shortClassName} correspond to a class in the library or null otherwise
   */
  @Contract(pure = true)
  public abstract @Nullable ExternalClassResolveResult resolveClass(@NotNull String shortClassName,
                                                          @NotNull ThreeState isAnnotation,
                                                          @NotNull Module contextModule);

  /**
   * Detects if a full reference text corresponds to the package in the library, e.g., inside on a demand import statement
   * 
   * @return library descriptor if {@code packageName} correspond to the package in the library or null otherwise
   */
  @Contract(pure = true)
  public @Nullable ExternalLibraryDescriptor resolvePackage(@NotNull String packageName) {
    return null;
  }

  public static final class ExternalClassResolveResult {
    private final String myQualifiedClassName;
    private final ExternalLibraryDescriptor myLibrary;

    public ExternalClassResolveResult(@NotNull String qualifiedClassName,
                                      @NotNull ExternalLibraryDescriptor library) {
      myQualifiedClassName = qualifiedClassName;
      myLibrary = library;
    }

    public @NotNull String getQualifiedClassName() {
      return myQualifiedClassName;
    }

    public @NotNull ExternalLibraryDescriptor getLibraryDescriptor() {
      return myLibrary;
    }
  }
}
