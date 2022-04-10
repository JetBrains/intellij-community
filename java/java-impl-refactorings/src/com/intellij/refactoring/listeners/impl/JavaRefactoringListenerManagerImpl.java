// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.listeners.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.MoveMemberListener;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public class JavaRefactoringListenerManagerImpl extends JavaRefactoringListenerManager {
  private final List<MoveMemberListener> myMoveMemberListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  @Override
  public void addMoveMembersListener(@NotNull MoveMemberListener moveMembersListener) {
    myMoveMemberListeners.add(moveMembersListener);
  }

  @Override
  public void removeMoveMembersListener(@NotNull MoveMemberListener moveMembersListener) {
    myMoveMemberListeners.remove(moveMembersListener);
  }

  public void fireMemberMoved(@NotNull PsiClass sourceClass, @NotNull PsiMember member) {
    for (final MoveMemberListener listener : myMoveMemberListeners) {
      listener.memberMoved(sourceClass, member);
    }
  }
}
