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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JavaClassListReferenceProvider extends JavaClassReferenceProvider {

  public JavaClassListReferenceProvider() {
    setOption(ADVANCED_RESOLVE, Boolean.TRUE);
  }

  @Override
  @NotNull
  public PsiReference[] getReferencesByString(String str, @NotNull final PsiElement position, int offsetInPosition){
    if (position instanceof XmlTag && ((XmlTag)position).getValue().getTextElements().length == 0) {
      return PsiReference.EMPTY_ARRAY; 
    }

    if (str.length() < 2) {
      return PsiReference.EMPTY_ARRAY;
    }

    int offset = position.getTextRange().getStartOffset() + offsetInPosition;
    for(PsiElement child = position.getFirstChild(); child != null; child = child.getNextSibling()){
      if (child instanceof OuterLanguageElement && child.getTextRange().contains(offset)) {
        return PsiReference.EMPTY_ARRAY;
      }
    }

    NotNullLazyValue<Set<String>> topLevelPackages = new NotNullLazyValue<Set<String>>() {
      @NotNull
      @Override
      protected Set<String> compute() {
        Set<String> knownTopLevelPackages = new HashSet<>();
        List<PsiPackage> defaultPackages = getDefaultPackages(position.getProject());
        for (PsiElement pack : defaultPackages) {
          if (pack instanceof PsiPackage) {
            knownTopLevelPackages.add(((PsiPackage)pack).getName());
          }
        }
        return knownTopLevelPackages;
      }
    };
    final List<PsiReference> results = new ArrayList<>();

    for(int dot = str.indexOf('.'); dot > 0; dot = str.indexOf('.', dot + 1)) {
      int start = dot;
      while (start > 0 && Character.isLetterOrDigit(str.charAt(start - 1))) start--;
      if (dot == start) {
        continue;
      }
      String candidate = str.substring(start, dot);
      if (topLevelPackages.getValue().contains(candidate)) {
        int end = dot;
        while (end < str.length() - 1) {
          end++;
          char ch = str.charAt(end);
          if (ch != '.' && !Character.isJavaIdentifierPart(ch)) {
            break;
          }
        }
        String s = str.substring(start, end + 1);
        ContainerUtil.addAll(results, new JavaClassReferenceSet(s, position, offsetInPosition + start, false, this) {
          @Override
          public boolean isSoft() {
            return true;
          }

          @Override
          public boolean isAllowDollarInNames() {
            return true;
          }
        }.getAllReferences());
        ProgressManager.checkCanceled();
      }
    }
    return ContainerUtil.toArray(results, new PsiReference[results.size()]);
  }

  @Override
  public GlobalSearchScope getScope(Project project) {
    return GlobalSearchScope.allScope(project);
  }
}
