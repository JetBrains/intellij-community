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
package com.intellij.lang.java;

import com.intellij.psi.PsiClass;
import com.intellij.refactoring.classMembers.ClassMembersRefactoringSupport;
import com.intellij.refactoring.classMembers.DependentMembersCollectorBase;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.util.classMembers.ClassMembersUtil;
import com.intellij.refactoring.util.classMembers.DependentMembersCollector;

/**
 * @author Dennis.Ushakov
 */
public class JavaClassMembersRefactoringSupport implements ClassMembersRefactoringSupport {
  @Override
  public DependentMembersCollectorBase createDependentMembersCollector(Object clazz, Object superClass) {
    return new DependentMembersCollector((PsiClass)clazz, (PsiClass)superClass);
  }

  @Override
  public boolean isProperMember(MemberInfoBase member) {
    return ClassMembersUtil.isProperMember(member);
  }
}
