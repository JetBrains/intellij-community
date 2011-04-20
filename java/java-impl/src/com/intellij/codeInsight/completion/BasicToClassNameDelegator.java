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

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * @author peter
 */
public class BasicToClassNameDelegator extends AbstractBasicToClassNameDelegator {

  @Override
  protected boolean isClassNameCompletionSupported(CompletionResultSet result, PsiFile file, PsiElement position) {
    return file.getLanguage().isKindOf(StdLanguages.XML) && StringUtil.isCapitalized(result.getPrefixMatcher().getPrefix());
  }

  @Override
  protected void updateProperties(LookupElement lookupElement) {
    JavaPsiClassReferenceElement classElement = lookupElement.as(JavaPsiClassReferenceElement.CLASS_CONDITION_KEY);
    if (classElement != null) {
      classElement.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
    }
    lookupElement.putUserData(XmlCompletionContributor.WORD_COMPLETION_COMPATIBLE, Boolean.TRUE); //todo think of a less dirty interaction
  }

}
