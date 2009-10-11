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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 09.07.2002
 * Time: 14:58:46
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.classMembers;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;


public interface MemberInfoModel<T extends PsiElement, M extends MemberInfoBase<T>> extends MemberInfoChangeListener<T, M> {
  int OK = 0;
  int WARNING = 1;
  int ERROR = 2;

  boolean isMemberEnabled(M member);

  boolean isCheckedWhenDisabled(M member);

  boolean isAbstractEnabled(M member);

  boolean isAbstractWhenDisabled(M member);

  /**
   * Returns state of abstract checkbox for particular abstract member.
   * @param member MemberInfo for an ABSTRACT member
   * @return TRUE if fixed and true, FALSE if fixed and false, null if dont care
   */
  Boolean isFixedAbstract(M member);

  int checkForProblems(@NotNull M member);

  String getTooltipText(M member);
}
