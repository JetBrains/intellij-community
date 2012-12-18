/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.LeftNeighbour;
import com.intellij.psi.filters.position.SuperParentFilter;

/**
 * @author ven
 */
public class Java15CompletionData extends JavaCompletionData {

  @Override
  protected void initVariantsInFileScope() {
    super.initVariantsInFileScope();
    //static keyword in static import
    {
      final CompletionVariant variant = new CompletionVariant(PsiImportList.class, new LeftNeighbour(new TextFilter (PsiKeyword.IMPORT)));
      variant.addCompletion(PsiKeyword.STATIC, TailType.HUMBLE_SPACE_BEFORE_WORD);

      registerVariant(variant);
    }

    {
      final ElementFilter position = new AndFilter(new LeftNeighbour(new TextFilter("@")),
                                                   new NotFilter(new SuperParentFilter(
                                                     new OrFilter(new ClassFilter(PsiNameValuePair.class),
                                                         new ClassFilter(PsiParameterList.class))))
                                                   );

      final CompletionVariant variant = new CompletionVariant(PsiJavaFile.class, position);
      variant.includeScopeClass(PsiClass.class);

      variant.addCompletion(PsiKeyword.INTERFACE, TailType.HUMBLE_SPACE_BEFORE_WORD);

      registerVariant(variant);
    }
  }

}
