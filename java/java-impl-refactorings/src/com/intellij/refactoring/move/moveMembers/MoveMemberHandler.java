// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveMembers;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.psi.*;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public interface MoveMemberHandler {
  LanguageExtension<MoveMemberHandler> EP_NAME = new LanguageExtension<>("com.intellij.refactoring.moveMemberHandler");

  @Nullable
  MoveMembersProcessor.MoveMembersUsageInfo getUsage(@NotNull PsiMember member,
                                                     @NotNull PsiReference ref,
                                                     @NotNull Set<PsiMember> membersToMove,
                                                     @NotNull PsiClass targetClass);

  default void checkConflictsOnUsage(@NotNull MoveMembersProcessor.MoveMembersUsageInfo usageInfo,
                                     @Nullable PsiModifierList modifierListCopy,
                                     @NotNull PsiClass targetClass,
                                     @NotNull Set<PsiMember> membersToMove,
                                     MoveMembersOptions moveMembersOptions,
                                     @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    checkConflictsOnUsage(usageInfo, moveMembersOptions.getExplicitMemberVisibility(), modifierListCopy, targetClass, membersToMove, conflicts);
  }

  default void checkConflictsOnUsage(@NotNull MoveMembersProcessor.MoveMembersUsageInfo usageInfo,
                                     @Nullable String newVisibility,
                                     @Nullable PsiModifierList modifierListCopy,
                                     @NotNull PsiClass targetClass,
                                     @NotNull Set<PsiMember> membersToMove,
                                     @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {}

  void checkConflictsOnMember(@NotNull PsiMember member,
                              @Nullable String newVisibility,
                              @Nullable PsiModifierList modifierListCopy,
                              @NotNull PsiClass targetClass,
                              @NotNull Set<PsiMember> membersToMove,
                              @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts);

  @Nullable
  PsiElement getAnchor(@NotNull PsiMember member, @NotNull PsiClass targetClass, Set<PsiMember> membersToMove);

  boolean changeExternalUsage(@NotNull MoveMembersOptions options, @NotNull MoveMembersProcessor.MoveMembersUsageInfo usage);

  @NotNull
  PsiMember doMove(@NotNull MoveMembersOptions options,
                   @NotNull PsiMember member,
                   @Nullable PsiElement anchor,
                   @NotNull PsiClass targetClass);

  void decodeContextInfo(@NotNull PsiElement scope);
}
