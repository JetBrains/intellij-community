/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.JBIterable;

import java.util.Collections;
import java.util.List;

public class InjectedReferenceSelectioner extends AbstractWordSelectioner {
  @Override
  public boolean canSelect(PsiElement e) {
    return PsiTreeUtil.getParentOfType(e, PsiLanguageInjectionHost.class) != null;
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, final int cursorOffset, Editor editor) {
    PsiReference ref = e.findReferenceAt(cursorOffset - e.getTextRange().getStartOffset());
    if (ref == null) return Collections.emptyList();
    JBIterable<PsiReference> it = ref instanceof PsiMultiReference ? JBIterable.of(((PsiMultiReference)ref).getReferences()) : JBIterable.of(ref);
    return it.transform(new Function<PsiReference, TextRange>() {
      @Override
      public TextRange fun(PsiReference ref) {
        TextRange base = ref.getElement().getTextRange();
        TextRange r = ref.getRangeInElement().shiftRight(base.getStartOffset());
        return r.containsOffset(cursorOffset) ? r : null;
      }
    }).filter(Conditions.notNull()).toList();
  }

}
