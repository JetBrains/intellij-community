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
package com.intellij.refactoring.move.moveMembers;

import com.intellij.lang.LanguageExtension;
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

  void checkConflictsOnUsage(@NotNull MoveMembersProcessor.MoveMembersUsageInfo usageInfo,
                             @Nullable String newVisibility,
                             @Nullable PsiModifierList modifierListCopy,
                             @NotNull PsiClass targetClass,
                             @NotNull Set<PsiMember> membersToMove,
                             @NotNull MultiMap<PsiElement, String> conflicts);

  void checkConflictsOnMember(@NotNull PsiMember member,
                              @Nullable String newVisibility,
                              @Nullable PsiModifierList modifierListCopy,
                              @NotNull PsiClass targetClass,
                              @NotNull Set<PsiMember> membersToMove,
                              @NotNull MultiMap<PsiElement, String> conflicts);

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
