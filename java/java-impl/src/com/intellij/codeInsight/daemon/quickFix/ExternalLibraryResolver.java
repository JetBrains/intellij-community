// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows detecting library by unresolved class/package name in the editor.
 * 
 * If external library is detected, e.g. junit or JB annotations library, {@link AddExtLibraryDependencyFix} is registered to configure that missed library
 */
public abstract class ExternalLibraryResolver {
  public static final ExtensionPointName<ExternalLibraryResolver> EP_NAME = ExtensionPointName.create("com.intellij.codeInsight.externalLibraryResolver");

  /**
   * Detects if short name may belong to the library's class
   * 
   * @param isAnnotation {@code ThreeState.YES} if reference is inside annotation,
   *                     {@code ThreeState.UNSURE} in imports, 
   *                     {@code ThreeState.NO} otherwise
   * @param contextModule can be used if e.g. different versions must be used based on language level of the module or similar
   * @return library descriptor if {@code shortClassName} correspond to a class in the library or null otherwise
   */
  @Nullable
  @Contract(pure = true)
  public abstract ExternalClassResolveResult resolveClass(@NotNull String shortClassName,
                                                          @NotNull ThreeState isAnnotation,
                                                          @NotNull Module contextModule);

  /**
   * Detects if full reference text corresponds to the package in the library, e.g. inside on demand import statement
   * 
   * @return library descriptor if {@code packageName} correspond to the package in the library or null otherwise
   */
  @Nullable
  @Contract(pure = true)
  public ExternalLibraryDescriptor resolvePackage(@NotNull String packageName) {
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

    @NotNull
    public String getQualifiedClassName() {
      return myQualifiedClassName;
    }

    @NotNull
    public ExternalLibraryDescriptor getLibraryDescriptor() {
      return myLibrary;
    }
  }
}
