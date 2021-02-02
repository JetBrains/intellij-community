// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  public PsiReference @NotNull [] getReferencesByString(String str, @NotNull final PsiElement position, int offsetInPosition){
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

    NotNullLazyValue<Set<String>> topLevelPackages = NotNullLazyValue.createValue(() -> {
      Set<String> knownTopLevelPackages = new HashSet<>();
      List<PsiPackage> defaultPackages = getDefaultPackages(position.getProject());
      for (PsiPackage pack : defaultPackages) {
        knownTopLevelPackages.add(pack.getName());
      }
      return knownTopLevelPackages;
    });
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
    return results.toArray(PsiReference.EMPTY_ARRAY);
  }

  @Override
  public GlobalSearchScope getScope(Project project) {
    return GlobalSearchScope.allScope(project);
  }
}
