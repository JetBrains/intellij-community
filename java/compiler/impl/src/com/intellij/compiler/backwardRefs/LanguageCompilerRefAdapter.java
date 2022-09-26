// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.backwardRefs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.backwardRefs.CompilerRef;
import org.jetbrains.jps.backwardRefs.NameEnumerator;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * An interface to provide connection between compact internal representation of indexed elements and PSI
 */
public interface LanguageCompilerRefAdapter {
  ExtensionPointName<LanguageCompilerRefAdapter> EP_NAME = ExtensionPointName.create("com.intellij.languageCompilerRefAdapter");

  @Nullable
  static LanguageCompilerRefAdapter findAdapter(@NotNull VirtualFile file) {
    final FileType fileType = file.getFileType();
    return findAdapter(fileType);
  }

  @Nullable
  static LanguageCompilerRefAdapter findAdapter(@NotNull VirtualFile file, boolean includeExternalLanguageHelper) {
    final FileType fileType = file.getFileType();
    return findAdapter(fileType, includeExternalLanguageHelper);
  }

  @Nullable
  static LanguageCompilerRefAdapter findAdapter(@NotNull PsiFile file, boolean includeExternalLanguageHelper) {
    final FileType fileType = file.getFileType();
    return findAdapter(fileType, includeExternalLanguageHelper);
  }

  @Nullable
  static LanguageCompilerRefAdapter findAdapter(@NotNull FileType fileType) {
    return findAdapter(fileType, false);
  }

  @Nullable
  static LanguageCompilerRefAdapter findAdapter(@NotNull FileType fileType, boolean includeExternalLanguageHelper) {
    for (LanguageCompilerRefAdapter adapter : EP_NAME.getExtensionList()) {
      if (adapter.getFileTypes().contains(fileType) ||
          includeExternalLanguageHelper && adapter instanceof ExternalLanguageHelper && adapter.getAffectedFileTypes().contains(fileType)) {
        return adapter;
      }
    }
    return null;
  }

  @Nullable
  static LanguageCompilerRefAdapter findAdapter(@NotNull PsiElement element) {
    return findAdapter(element, false);
  }

  @Nullable
  static LanguageCompilerRefAdapter findAdapter(@NotNull PsiElement element, boolean includeExternalLanguageHelper) {
    final PsiFile file = element.getContainingFile();
    return file == null ? null : findAdapter(file, includeExternalLanguageHelper);
  }

  @NotNull
  Set<FileType> getFileTypes();

  /**
   * @return file types that are involved in dirty scope computation
   */
  @NotNull
  default Set<FileType> getAffectedFileTypes() {
    return getFileTypes();
  }

  /**
   * @param element PSI element written in corresponding language
   * @param names enumerator to encode string names
   */
  @Nullable
  CompilerRef asCompilerRef(@NotNull PsiElement element, @NotNull NameEnumerator names) throws IOException;

  /**
   * @param element PSI element written in corresponding language
   * @param names enumerator to encode string names
   */
  default @Nullable List<@NotNull CompilerRef> asCompilerRefs(@NotNull PsiElement element, @NotNull NameEnumerator names)
    throws IOException {
    CompilerRef compilerRef = asCompilerRef(element, names);
    if (compilerRef == null) {
      return null;
    }

    return Collections.singletonList(compilerRef);
  }

  /**
   * @return "hierarchy" of given element inside the libraries scope.
   */
  @NotNull
  List<CompilerRef> getHierarchyRestrictedToLibraryScope(@NotNull CompilerRef baseRef,
                                                         @NotNull PsiElement basePsi,
                                                         @NotNull NameEnumerator names,
                                                         @NotNull GlobalSearchScope libraryScope) throws IOException;

  /**
   * class in java, class or object in some other jvm languages. used in direct inheritor search. This class object will be used to filter
   * inheritors of corresponding language among of other inheritors.
   *
   * name of this CompilerRef is always enumerated internal string name of language object, eg.: A$1$B
   */
  @NotNull
  Class<? extends CompilerRef.CompilerClassHierarchyElementDef> getHierarchyObjectClass();

  /**
   * functional expression: lambda or method reference. used in functional expression search
   *
   * name of this CompilerRef is always order-index inside source-code file
   */
  @NotNull
  Class<? extends CompilerRef> getFunExprClass();

  /**
   * @return classes that can be inheritors of given superClass. This method shouldn't directly check are
   * found elements really inheritors.
   */
  PsiElement @NotNull [] findDirectInheritorCandidatesInFile(SearchId @NotNull [] internalNames,
                                                             @NotNull PsiFileWithStubSupport file);

  /**
   * @param indices - ordinal-numbers (corresponding to compiler tree index visitor) of required functional expressions.
   * @return functional expressions for given functional type. Should return
   */
  PsiElement @NotNull [] findFunExpressionsInFile(SearchId @NotNull [] indices,
                                                  @NotNull PsiFileWithStubSupport file);

  boolean isClass(@NotNull PsiElement element);

  PsiElement @NotNull [] getInstantiableConstructors(@NotNull PsiElement aClass);

  boolean isDirectInheritor(PsiElement candidate, PsiNamedElement baseClass);

  /**
   * A class to extend existing {@link LanguageCompilerRefAdapter} to support other languages within existing compact internal
   * representation of indexed elements, e.g.: find Kotlin usages in Java files
   *
   * @see CompilerReferenceServiceBase
   */
  abstract class ExternalLanguageHelper implements LanguageCompilerRefAdapter {

    @Override
    final public @NotNull Set<FileType> getFileTypes() {
      return Collections.emptySet();
    }

    @Override
    abstract public @NotNull Set<FileType> getAffectedFileTypes();

    @Override
    final public @NotNull Class<? extends CompilerRef.CompilerClassHierarchyElementDef> getHierarchyObjectClass() {
      throw new UnsupportedOperationException();
    }

    @Override
    final public @NotNull Class<? extends CompilerRef> getFunExprClass() {
      throw new UnsupportedOperationException();
    }

    @Override
    final public PsiElement @NotNull [] findDirectInheritorCandidatesInFile(SearchId @NotNull [] internalNames,
                                                                            @NotNull PsiFileWithStubSupport file) {
      throw new UnsupportedOperationException();
    }

    @Override
    final public PsiElement @NotNull [] findFunExpressionsInFile(SearchId @NotNull [] indices,
                                                                 @NotNull PsiFileWithStubSupport file) {
      throw new UnsupportedOperationException();
    }

    @Override
    final public boolean isClass(@NotNull PsiElement element) {
      throw new UnsupportedOperationException();
    }

    @Override
    final public PsiElement @NotNull [] getInstantiableConstructors(@NotNull PsiElement aClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    final public boolean isDirectInheritor(PsiElement candidate, PsiNamedElement baseClass) {
      throw new UnsupportedOperationException();
    }
  }
}
