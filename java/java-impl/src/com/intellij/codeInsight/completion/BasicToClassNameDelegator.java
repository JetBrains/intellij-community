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
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;

/**
 * @author peter
 */
public class BasicToClassNameDelegator extends AbstractBasicToClassNameDelegator {

  @Override
  protected boolean isClassNameCompletionSupported(CompletionResultSet result, PsiFile file, PsiElement position) {
    final boolean isJava = file.getLanguage() == StdLanguages.JAVA;
    if (!isJava && !(file.getLanguage() instanceof XMLLanguage)) return false;

    if (isJava) {
      if (!(position.getParent() instanceof PsiJavaCodeReferenceElement)) return false;
      if (((PsiJavaCodeReferenceElement)position.getParent()).getQualifier() != null) return false;
    }

    final String s = result.getPrefixMatcher().getPrefix();
    if (StringUtil.isEmpty(s) || !Character.isUpperCase(s.charAt(0))) return false;
    return true;
  }

  @Override
  protected void updateProperties(LookupElement lookupElement) {
    if (lookupElement instanceof JavaPsiClassReferenceElement) {
      ((JavaPsiClassReferenceElement)lookupElement).setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
    }
    lookupElement.putUserData(XmlCompletionContributor.WORD_COMPLETION_COMPATIBLE, Boolean.TRUE); //todo think of a less dirty interaction
  }

}
