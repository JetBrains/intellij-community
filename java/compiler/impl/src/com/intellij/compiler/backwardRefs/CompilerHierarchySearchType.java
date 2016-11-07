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
    PsiElement[] performSearchInFile(Object[] definitions,
                                     PsiNamedElement baseElement,
                                     PsiFileWithStubSupport file,
                                     LanguageLightRefAdapter adapter) {
      return adapter.findDirectInheritorCandidatesInFile((String[])definitions, file, baseElement);
    }

    @Override
    Class<? extends LightRef> getRequiredClass(LanguageLightRefAdapter adapter) {
      return adapter.getHierarchyObjectClass();
    }

    @Override
    Object[] convertToIds(Collection<LightRef> lightRef, ByteArrayEnumerator byteArrayEnumerator) {
      return lightRef.stream().map(r -> byteArrayEnumerator.getName(((LightRef.LightClassHierarchyElementDef)r).getName())).toArray(String[]::new);
    }
  },
  FUNCTIONAL_EXPRESSION {
    @Override
    PsiElement[] performSearchInFile(Object[] definitions,
                                     PsiNamedElement baseElement,
                                     PsiFileWithStubSupport file,
                                     LanguageLightRefAdapter adapter) {
      return adapter.findFunExpressionsInFile((Integer[])definitions, file);
    }

    @Override
    Class<? extends LightRef> getRequiredClass(LanguageLightRefAdapter  adapter) {
      return adapter.getFunExprClass();
    }

    @Override
    Object[] convertToIds(Collection<LightRef> lightRef, ByteArrayEnumerator byteArrayEnumerator) {
      return lightRef.stream().map(r -> ((LightRef.LightFunExprDef) r).getId()).toArray(Integer[]::new);
    }
  };

  abstract PsiElement[] performSearchInFile(Object[] definitions,
                                            PsiNamedElement baseElement,
                                            PsiFileWithStubSupport file,
                                            LanguageLightRefAdapter adapter);

  abstract Class<? extends LightRef> getRequiredClass(LanguageLightRefAdapter adapter);

  abstract Object[] convertToIds(Collection<LightRef> lightRef, ByteArrayEnumerator byteArrayEnumerator);
}
