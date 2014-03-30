/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup.sub;

import com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup.ChainCompletionLookupElementUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class StaticMethodSubLookupElement implements SubLookupElement {

  private final PsiMethod myMethod;
  private final TIntObjectHashMap<SubLookupElement> myReplaceElements;

  public StaticMethodSubLookupElement(final PsiMethod method, @Nullable final TIntObjectHashMap<SubLookupElement> replaceElements) {
    assert method.hasModifierProperty(PsiModifier.STATIC);
    myReplaceElements = replaceElements;
    myMethod = method;
  }

  @Override
  public void doImport(final PsiJavaFile javaFile) {
    final PsiClass containingClass = myMethod.getContainingClass();
    if (containingClass != null) {
      if (javaFile.findImportReferenceTo(containingClass) == null) {
        javaFile.importClass(containingClass);
      }
    }
    if (myReplaceElements != null) {
      myReplaceElements.forEachValue(new TObjectProcedure<SubLookupElement>() {
        @Override
        public boolean execute(final SubLookupElement subLookupElement) {
          subLookupElement.doImport(javaFile);
          return false;
        }
      });
    }
  }

  @Override
  public String getInsertString() {
    //noinspection ConstantConditions
    return String.format("%s.%s(%s)", myMethod.getContainingClass().getName(), myMethod.getName(),
                         ChainCompletionLookupElementUtil.fillMethodParameters(myMethod, myReplaceElements));
  }
}
