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
 * Time: 15:41:29
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.classMembers;

import com.intellij.psi.PsiElement;

import java.util.Collection;

public class MemberInfoChange<T extends PsiElement, U extends MemberInfoBase<T>> {
  private final Collection<U> myChangedMembers;

  public MemberInfoChange(Collection<U> changedMembers) {
    myChangedMembers = changedMembers;
  }

  public Collection<U> getChangedMembers() {
    return myChangedMembers;
  }
}
