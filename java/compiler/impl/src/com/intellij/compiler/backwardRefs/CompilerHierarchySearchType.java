/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.backwardRefs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import org.jetbrains.jps.backwardRefs.ByteArrayEnumerator;
import org.jetbrains.jps.backwardRefs.LightRef;

import java.util.Collection;

enum CompilerHierarchySearchType {
  DIRECT_INHERITOR {
    @Override
    <T extends PsiElement> T[] performSearchInFile(Collection<LightRef> definitions,
                                                   PsiNamedElement baseElement,
                                                   ByteArrayEnumerator nameEnumerator,
                                                   PsiFileWithStubSupport file,
                                                   LanguageLightRefAdapter adapter) {
      return (T[])adapter.findDirectInheritorCandidatesInFile(definitions, nameEnumerator, file, baseElement);
    }

    @Override
    Class<? extends LightRef> getRequiredClass(LanguageLightRefAdapter<?, ?> adapter) {
      return adapter.getHierarchyObjectClass();
    }
  },
  FUNCTIONAL_EXPRESSION {
    @Override
    <T extends PsiElement> T[] performSearchInFile(Collection<LightRef> definitions,
                                                   PsiNamedElement baseElement,
                                                   ByteArrayEnumerator nameEnumerator,
                                                   PsiFileWithStubSupport file,
                                                   LanguageLightRefAdapter adapter) {
      return (T[])adapter.findFunExpressionsInFile(definitions, file);
    }

    @Override
    Class<? extends LightRef> getRequiredClass(LanguageLightRefAdapter<?, ?> adapter) {
      return adapter.getFunExprClass();
    }
  };

  abstract <T extends PsiElement> T[] performSearchInFile(Collection<LightRef> definitions,
                                                          PsiNamedElement baseElement,
                                                          ByteArrayEnumerator nameEnumerator,
                                                          PsiFileWithStubSupport file,
                                                          LanguageLightRefAdapter adapter);

  abstract Class<? extends LightRef> getRequiredClass(LanguageLightRefAdapter<?, ?> adapter);
}
