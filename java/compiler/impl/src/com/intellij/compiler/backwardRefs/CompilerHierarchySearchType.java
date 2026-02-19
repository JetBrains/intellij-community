// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.backwardRefs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.CompilerRef;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public enum CompilerHierarchySearchType {
  DIRECT_INHERITOR {
    @Override
    PsiElement[] performSearchInFile(SearchId[] definitions,
                                     PsiNamedElement baseElement,
                                     PsiFileWithStubSupport file,
                                     LanguageCompilerRefAdapter adapter) {
      return adapter.findDirectInheritorCandidatesInFile(definitions, file);
    }

    @Override
    Class<? extends CompilerRef> getRequiredClass(LanguageCompilerRefAdapter adapter) {
      return adapter.getHierarchyObjectClass();
    }

    @Override
    @NotNull SearchId convertToId(@NotNull CompilerRef compilerRef, PersistentStringEnumerator nameEnumerator) throws IOException {
      return compilerRef instanceof CompilerRef.JavaCompilerAnonymousClassRef
                    ? new SearchId(((CompilerRef.JavaCompilerAnonymousClassRef)compilerRef).getName())
                    : new SearchId(nameEnumerator.valueOf(((CompilerRef.CompilerClassHierarchyElementDef)compilerRef).getName()));
    }
  },
  FUNCTIONAL_EXPRESSION {
    @Override
    PsiElement[] performSearchInFile(SearchId[] definitions,
                                     PsiNamedElement baseElement,
                                     PsiFileWithStubSupport file,
                                     LanguageCompilerRefAdapter adapter) {
      return adapter.findFunExpressionsInFile(definitions, file);
    }

    @Override
    Class<? extends CompilerRef> getRequiredClass(LanguageCompilerRefAdapter adapter) {
      return adapter.getFunExprClass();
    }

    @Override
    @NotNull SearchId convertToId(@NotNull CompilerRef compilerRef, PersistentStringEnumerator nameEnumerator) {
      return new SearchId(((CompilerRef.CompilerFunExprDef) compilerRef).getId());
    }
  };

  abstract PsiElement[] performSearchInFile(SearchId[] definitions,
                                            PsiNamedElement baseElement,
                                            PsiFileWithStubSupport file,
                                            LanguageCompilerRefAdapter adapter);

  abstract Class<? extends CompilerRef> getRequiredClass(LanguageCompilerRefAdapter adapter);

  abstract @NotNull SearchId convertToId(@NotNull CompilerRef compilerRef, PersistentStringEnumerator nameEnumerator) throws IOException;

  @NotNull SearchId @NotNull[] convertToIds(Collection<? extends CompilerRef> compilerRef, PersistentStringEnumerator nameEnumerator) throws IOException {
    List<SearchId> list = new ArrayList<>(compilerRef.size());
    for (CompilerRef r : compilerRef) {
      list.add(convertToId(r, nameEnumerator));
    }

    return list.toArray(new SearchId[0]);
  }
}
