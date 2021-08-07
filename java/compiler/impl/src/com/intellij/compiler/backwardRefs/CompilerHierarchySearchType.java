// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.backwardRefs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.util.io.PersistentStringEnumerator;
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
    SearchId[] convertToIds(Collection<CompilerRef> compilerRef, PersistentStringEnumerator nameEnumerator) throws IOException {
      List<SearchId> list = new ArrayList<>();
      for (CompilerRef r : compilerRef) {
        SearchId id = r instanceof CompilerRef.JavaCompilerAnonymousClassRef
                      ? new SearchId(((CompilerRef.JavaCompilerAnonymousClassRef)r).getName())
                      : new SearchId(nameEnumerator.valueOf(((CompilerRef.CompilerClassHierarchyElementDef)r).getName()));
        list.add(id);
      }
      return list.toArray(new SearchId[0]);
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
    SearchId[] convertToIds(Collection<CompilerRef> compilerRef, PersistentStringEnumerator nameEnumerator) {
      return compilerRef.stream().map(r -> ((CompilerRef.CompilerFunExprDef) r).getId()).map(SearchId::new).toArray(SearchId[]::new);
    }
  };

  abstract PsiElement[] performSearchInFile(SearchId[] definitions,
                                            PsiNamedElement baseElement,
                                            PsiFileWithStubSupport file,
                                            LanguageCompilerRefAdapter adapter);

  abstract Class<? extends CompilerRef> getRequiredClass(LanguageCompilerRefAdapter adapter);

  abstract SearchId[] convertToIds(Collection<CompilerRef> compilerRef, PersistentStringEnumerator nameEnumerator) throws IOException;

}
