// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.*;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

abstract class StaticMembersProcessor<T extends PsiMember & PsiDocCommentOwner> implements Processor<T> {
  private final MultiMap<PsiClass, T> mySuggestions = MultiMap.createLinked();

  private final Map<String, Boolean> myPossibleClasses = new HashMap<>();

  private final @NotNull PsiElement myPlace;
  private final boolean myInDefaultPackage;
  private final boolean myAddStaticImport;
  private ExpectedTypeInfo[] myExpectedTypes;
  private final int myMaxResults;

  StaticMembersProcessor(@NotNull PsiElement place, boolean addStaticImport, int maxResults) {
    myPlace = place;
    myMaxResults = maxResults;
    myInDefaultPackage = PsiUtil.isFromDefaultPackage(place);
    myAddStaticImport = addStaticImport;
  }

  enum ApplicableType {NONE, PARTLY, APPLICABLE}

  protected abstract ApplicableType isApplicable(@NotNull T member, @NotNull PsiElement place);

  record MembersToImport<K>(@NotNull List<K> applicable,@NotNull List<K> all) {
  }

  @NotNull MembersToImport<T> getMembersToImport() {
    final List<Pair<T, ApplicableType>> list = new ArrayList<>();
    final List<T> applicableList = new ArrayList<>();
    for (Map.Entry<PsiClass, Collection<T>> methodEntry : mySuggestions.entrySet()) {
      registerMember(methodEntry.getKey(), methodEntry.getValue(), list, applicableList);
    }

    Comparator<T> comparator = CodeInsightUtil.createSortIdenticalNamedMembersComparator(myPlace);
    if (applicableList.isEmpty()) {
      list.sort(Comparator.<Pair<T, ApplicableType>, ApplicableType>comparing(t -> t.getSecond(), Comparator.naturalOrder())
                  .reversed()
                  .thenComparing(t -> t.getFirst(), comparator));
    }
    else {
      applicableList.sort(comparator);
      applicableList.addAll(list.stream()
                              .filter(t -> t.getSecond() == ApplicableType.PARTLY)
                              .map(t -> t.getFirst())
                              .sorted(comparator)
                              .toList());
    }
    return new MembersToImport<>(applicableList, ContainerUtil.map(list, t -> t.getFirst()));
  }

  protected ExpectedTypeInfo @NotNull [] getExpectedTypes() {
    if (myExpectedTypes == null) {
      if (myPlace instanceof PsiExpression) {
        myExpectedTypes = ExpectedTypesProvider.getExpectedTypes((PsiExpression)myPlace, false);
      }
      else {
        myExpectedTypes = ExpectedTypeInfo.EMPTY_ARRAY;
      }
    }
    return myExpectedTypes;
  }

  protected ApplicableType isApplicableFor(@NotNull PsiType fieldType) {
    ExpectedTypeInfo[] expectedTypes = getExpectedTypes();
    for (ExpectedTypeInfo info : expectedTypes) {
      if (TypeConversionUtil.isAssignable(info.getType(), fieldType)) return ApplicableType.APPLICABLE;
      if (info.getType() instanceof PsiClassType classType &&
          classType.getParameters().length != 0 &&
          TypeConversionUtil.isAssignable(TypeConversionUtil.erasure(info.getType()), fieldType)) {
        return ApplicableType.PARTLY;
      }
    }
    return expectedTypes.length == 0 ? ApplicableType.APPLICABLE : ApplicableType.NONE;
  }

  @Override
  public boolean process(T member) {
    ProgressManager.checkCanceled();
    if (isExcluded(member)) {
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
        //methods in interfaces must have explicit static modifier, or they are not static;
        return true;
      }

      if (myAddStaticImport) {
        if (!PsiUtil.isFromDefaultPackage(member)) {
          mySuggestions.putValue(containingClass, member);
        }
      }
      else if (myInDefaultPackage || !PsiUtil.isFromDefaultPackage(member)) {
        mySuggestions.putValue(containingClass, member);
      }
    }

    return mySuggestions.size() < myMaxResults;
  }

  private static boolean isExcluded(@NotNull PsiMember method) {
    String name = PsiUtil.getMemberQualifiedName(method);
    return name != null && JavaProjectCodeInsightSettings.getSettings(method.getProject()).isExcluded(name);
  }

  private void registerMember(@NotNull PsiClass containingClass,
                              @NotNull Collection<? extends T> members,
                              @NotNull List<Pair<T, ApplicableType>> list,
                              @NotNull List<T> applicableList) {
    String qualifiedName = containingClass.getQualifiedName();
    if (qualifiedName == null) {
      return;
    }

    Boolean alreadyMentioned = myPossibleClasses.get(qualifiedName);
    if (alreadyMentioned == Boolean.TRUE) return;
    if (alreadyMentioned == null) {
      myPossibleClasses.put(qualifiedName, false);
    }
    for (T member : members) {
      if (!member.hasModifierProperty(PsiModifier.STATIC)) {
        continue;
      }

      if (alreadyMentioned == null) {
        list.add(new Pair<>(member, ApplicableType.NONE));
        alreadyMentioned = Boolean.FALSE;
      }

      if (!PsiUtil.isAccessible(myPlace.getProject(), member, myPlace, containingClass)) {
        continue;
      }

      if (myAddStaticImport && !PsiUtil.isAccessible(myPlace.getProject(), member, myPlace.getContainingFile(), containingClass)) {
        continue;
      }

      ApplicableType type = isApplicable(member, myPlace);
      if (!list.isEmpty()) {
        Pair<T, ApplicableType> previousPair = list.get(list.size() - 1);
        if (previousPair.getFirst().getContainingClass() == containingClass &&
            previousPair.getSecond().ordinal() < type.ordinal()) {
          list.remove(list.size() - 1);
          list.add(new Pair<>(member, type));
        }
      }

      if (type == ApplicableType.APPLICABLE) {
        applicableList.add(member);
        myPossibleClasses.put(qualifiedName, true);
        break;
      }
    }
  }
}
