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
package com.intellij.refactoring.listeners.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.MoveMemberListener;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

/**
 * @author yole
 */
public class JavaRefactoringListenerManagerImpl extends JavaRefactoringListenerManager {
  private final List<MoveMemberListener> myMoveMemberListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public void addMoveMembersListener(MoveMemberListener moveMembersListener) {
    myMoveMemberListeners.add(moveMembersListener);
  }

  public void removeMoveMembersListener(MoveMemberListener moveMembersListener) {
    myMoveMemberListeners.remove(moveMembersListener);
  }

  public void fireMemberMoved(final PsiClass sourceClass, final PsiMember member) {
    for (final MoveMemberListener listener : myMoveMemberListeners) {
      listener.memberMoved(sourceClass, member);
    }
  }
}
