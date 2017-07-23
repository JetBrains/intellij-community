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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.ImportFilter;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.LinkedMultiMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

abstract class StaticMembersProcessor<T extends PsiMember & PsiDocCommentOwner> implements Processor<T> {
  public enum SearchMode {
    MAX_2_MEMBERS(2),
    MAX_100_MEMBERS(100);

    private final int count;
    SearchMode(int count) {
      this.count = count;
    }
  }

  private final MultiMap<PsiClass, T> mySuggestions = new LinkedMultiMap<>();

  private final Map<PsiClass, Boolean> myPossibleClasses = new HashMap<>();

  @NotNull private final PsiElement myPlace;
  @NotNull private final SearchMode mySearchMode;
  private final boolean myShowMembersFromDefaultPackage;
  private PsiType myExpectedType;

  protected StaticMembersProcessor(@NotNull PsiElement place,
                                   boolean showMembersFromDefaultPackage,
                                   @NotNull SearchMode searchMode) {
    myPlace = place;
    mySearchMode = searchMode;
    myShowMembersFromDefaultPackage = showMembersFromDefaultPackage && PsiUtil.isFromDefaultPackage(place);
    myExpectedType = PsiType.NULL;
  }

  protected abstract boolean isApplicable(T member, PsiElement place);

  @NotNull
  public List<T> getMembersToImport(boolean applicableOnly) {
    final List<T> list = new ArrayList<>();
    final List<T> applicableList = new ArrayList<>();
    for (Map.Entry<PsiClass, Collection<T>> methodEntry : mySuggestions.entrySet()) {
      registerMember(methodEntry.getKey(), methodEntry.getValue(), list, applicableList);
    }

    List<T> result = !applicableOnly && applicableList.isEmpty() ? list : applicableList;
    Collections.sort(result, CodeInsightUtil.createSortIdenticalNamedMembersComparator(myPlace));
    return result;
  }

  public PsiType getExpectedType() {
    if (myExpectedType == PsiType.NULL) {
      myExpectedType = getExpectedTypeInternal();
    }
    return myExpectedType;
  }

  private PsiType getExpectedTypeInternal() {
    if (!(myPlace instanceof PsiExpression)) return null;
    ExpectedTypeInfo[] types = ExpectedTypesProvider.getExpectedTypes((PsiExpression)myPlace, false);
    return types.length > 0 ? types[0].getType() : null;
  }

  @Override
  public boolean process(T member) {
    ProgressManager.checkCanceled();
    if (StaticImportMemberFix.isExcluded(member)) {
      return true;
    }
    final PsiClass containingClass = member.getContainingClass();
    if (containingClass != null) {
      final String qualifiedName = containingClass.getQualifiedName();
      final PsiFile containingFile = myPlace.getContainingFile();
      if (qualifiedName != null && containingFile != null && !ImportFilter.shouldImport(containingFile, qualifiedName)) {
        return true;
      }

      PsiModifierList modifierList = member.getModifierList();
      if (modifierList != null && member instanceof PsiMethod &&
          member.getLanguage().isKindOf(JavaLanguage.INSTANCE)
          && !modifierList.hasExplicitModifier(PsiModifier.STATIC)) {
        //methods in interfaces must have explicit static modifier or they are not static;
        return true;
      }
    }

    if (myShowMembersFromDefaultPackage || !PsiUtil.isFromDefaultPackage(member)) {
      mySuggestions.putValue(containingClass, member);
    }
    return processCondition();
  }

  private boolean processCondition() {
    return mySuggestions.size() < mySearchMode.count;
  }

  private void registerMember(PsiClass containingClass,
                              Collection<T> members,
                              List<T> list,
                              List<T> applicableList) {
    Boolean alreadyMentioned = myPossibleClasses.get(containingClass);
    if (alreadyMentioned == Boolean.TRUE) return;
    if (containingClass.getQualifiedName() == null) {
      return;
    }
    if (alreadyMentioned == null) {
      myPossibleClasses.put(containingClass, false);
    }
    for (T member : members) {
      if (!member.hasModifierProperty(PsiModifier.STATIC)) {
        continue;
      }

      if (alreadyMentioned == null) {
        list.add(member);
        alreadyMentioned = Boolean.FALSE;
      }

      if (!PsiUtil.isAccessible(myPlace.getProject(), member, myPlace, containingClass)) {
        continue;
      }
      if (isApplicable(member, myPlace)) {
        applicableList.add(member);
        myPossibleClasses.put(containingClass, true);
        break;
      }
    }
  }
}
