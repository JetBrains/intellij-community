/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.refactoring.util.duplicates;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.List;

class ConstantMatchProvider implements MatchProvider {
  private final PsiField myField;
  private final Project myProject;
  private final List<Match> myMatches;
  private static final Logger LOG = Logger.getInstance(ConstantMatchProvider.class);

  ConstantMatchProvider(PsiMember member, Project project, List<Match> matches) {
    myField = (PsiField)member;
    myProject = project;
    myMatches = matches;
  }

  @Override
  public void prepareSignature(Match match) {}

  @Override
  public PsiElement processMatch(Match match) throws IncorrectOperationException {
    final PsiClass containingClass = myField.getContainingClass();
    LOG.assertTrue(containingClass != null, myField);
    String fieldReference = myField.getName();
    final PsiElement start = match.getMatchStart();
    if (!PsiTreeUtil.isAncestor(containingClass, start, false)) {
      fieldReference = containingClass.getQualifiedName() + "." + fieldReference; 
    }
    return match.replaceWithExpression(JavaPsiFacade.getElementFactory(myProject).createExpressionFromText(fieldReference, myField));
  }

  @Override
  public List<Match> getDuplicates() {
    return myMatches;
  }

  @Override
  public Boolean hasDuplicates() {
    return !myMatches.isEmpty();
  }

  @Override
  public String getConfirmDuplicatePrompt(Match match) {
    return null;
  }

  @Override
  public String getReplaceDuplicatesTitle(int idx, int size) {
    return JavaRefactoringBundle.message("process.duplicates.title", idx, size);
  }
}
