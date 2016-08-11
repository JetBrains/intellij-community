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
package com.intellij.refactoring.inline;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

/**
 * @author ven
 */
public class ReferencedElementsCollector extends JavaRecursiveElementVisitor {
  final HashSet<PsiMember> myReferencedMembers = new HashSet<>();

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitReferenceElement(expression);
  }

  @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    final PsiElement psiElement = reference.resolve();
    if (psiElement instanceof PsiMember) {
      checkAddMember((PsiMember)psiElement);
    }
    super.visitReferenceElement(reference);
  }

  protected void checkAddMember(@NotNull final PsiMember member) {
    myReferencedMembers.add(member);
  }
}
