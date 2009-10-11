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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public interface MoveMemberHandler {
  LanguageExtension<MoveMemberHandler> EP_NAME = new LanguageExtension<MoveMemberHandler>("com.intellij.refactoring.moveMemberHandler");

  MoveMembersProcessor.MoveMembersUsageInfo getUsage(PsiMember member,
                                                     PsiReference ref,
                                                     Set<PsiMember> membersToMove,
                                                     PsiClass targetClass);

  boolean changeExternalUsage(MoveMembersOptions options, MoveMembersProcessor.MoveMembersUsageInfo usage);

  PsiMember doMove(MoveMembersOptions options, PsiMember member, PsiElement anchor, PsiClass targetClass);

  void decodeContextInfo(PsiElement scope);

  @Nullable
  PsiElement getAnchor(PsiMember member, PsiClass targetClass);
}
