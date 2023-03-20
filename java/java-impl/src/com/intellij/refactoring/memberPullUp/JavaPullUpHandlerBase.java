// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.memberPullUp;

import com.intellij.psi.PsiClass;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import org.jetbrains.annotations.NotNull;

public interface JavaPullUpHandlerBase extends RefactoringActionHandler {
  void runSilently(@NotNull PsiClass sourceClass,
                   PsiClass targetSuperClass,
                   MemberInfo[] membersToMove,
                   DocCommentPolicy javaDocPolicy);
}
