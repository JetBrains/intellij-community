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
