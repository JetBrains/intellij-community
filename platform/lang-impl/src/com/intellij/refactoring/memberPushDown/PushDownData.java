// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.memberPushDown;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.util.DocCommentPolicy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PushDownData<MemberInfo extends MemberInfoBase<Member>,
                          Member extends PsiElement> {
  private PsiElement mySourceClass;
  private final List<MemberInfo> myMembersToMove;
  private final DocCommentPolicy myCommentPolicy;
  private final boolean myPreserveLinks;

  PushDownData(@NotNull PsiElement sourceClass,
               @NotNull List<MemberInfo> membersToMove,
               @NotNull DocCommentPolicy commentPolicy) {
    this(sourceClass, membersToMove, commentPolicy, false);
  }

  @ApiStatus.Experimental
  PushDownData(@NotNull PsiElement sourceClass,
               @NotNull List<MemberInfo> membersToMove,
               @NotNull DocCommentPolicy commentPolicy,
               boolean preserveExternalLinks) {
    mySourceClass = sourceClass;
    myMembersToMove = membersToMove;
    myCommentPolicy = commentPolicy;
    myPreserveLinks = preserveExternalLinks;
  }

  public boolean preserveExternalLinks() {
    return myPreserveLinks;
  }
  public @NotNull PsiElement getSourceClass() {
    return mySourceClass;
  }
  public @NotNull List<MemberInfo> getMembersToMove() {
    return myMembersToMove;
  }
  public @NotNull DocCommentPolicy getCommentPolicy() {
    return myCommentPolicy;
  }

  public void setSourceClass(@NotNull PsiElement sourceClass) {
    mySourceClass = sourceClass;
  }
}
