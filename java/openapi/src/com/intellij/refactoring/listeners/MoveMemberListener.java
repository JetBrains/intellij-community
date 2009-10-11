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

package com.intellij.refactoring.listeners;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;

/**
 * Notifies that a certain member has been moved.
 * This listener is invoked by pull up, push down and extract super refactorings.
 * To subscribe to move refactoring use {@link com.intellij.refactoring.listeners.RefactoringElementListener} class.
 * @author ven
 */
public interface MoveMemberListener {
  /**
   * @param sourceClass the class member was in before the refactoring
   * @param member the member that has been moved. To obtain target class use
   * {@link com.intellij.psi.PsiMember#getContainingClass()}. In all cases but
   * "Move inner to upper level" target class wil be non null.
   */
  void memberMoved (PsiClass sourceClass, PsiMember member);
}
