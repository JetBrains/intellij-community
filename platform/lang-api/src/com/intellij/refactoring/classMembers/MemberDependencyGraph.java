/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

package com.intellij.refactoring.classMembers;

import com.intellij.psi.PsiElement;

import java.util.Set;

public interface MemberDependencyGraph<T extends PsiElement, M extends MemberInfoBase<T>> {

  /**
   * Call this to notify that a new memberInfo has been added
   * or the state of some memberInfo has been changed.
   */
  void memberChanged(M memberInfo);

  /**
   * Returns class members that are dependent on checked MemberInfos.
   *
   * @return set of PsiMembers
   */
  Set<? extends T> getDependent();

  /**
   * Returns PsiMembers of checked MemberInfos that member depends on.
   * Member should belong to {@link #getDependent()}.
   *
   * @return set of PsiMembers
   */
  Set<? extends T> getDependenciesOf(T member);
}
