/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceVariable;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.rename.JavaUnresolvableLocalCollisionDetector;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.util.containers.MultiMap;

import java.util.HashSet;

public class InputValidator implements IntroduceVariableBase.Validator {
  private final Project myProject;
  private final ExpressionOccurrenceManager myOccurenceManager;
  private final IntroduceVariableBase myIntroduceVariableBase;

  @Override
  public boolean isOK(IntroduceVariableSettings settings) {
    String name = settings.getEnteredName();
    PsiExpression[] occurrences = settings.getReplaceChoice().filter(myOccurenceManager);
    final PsiElement anchor = IntroduceVariableBase.getAnchor(occurrences);
    if (anchor == null) return true;
    final PsiElement scope = anchor.getParent();
    if(scope == null) return true;
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    final HashSet<PsiVariable> reportedVariables = new HashSet<>();
    JavaUnresolvableLocalCollisionDetector.CollidingVariableVisitor visitor = new JavaUnresolvableLocalCollisionDetector.CollidingVariableVisitor() {
      @Override
      public void visitCollidingElement(PsiVariable collidingVariable) {
        if (!reportedVariables.contains(collidingVariable)) {
          reportedVariables.add(collidingVariable);
          String message = JavaRefactoringBundle.message("introduced.variable.will.conflict.with.0", RefactoringUIUtil.getDescription(collidingVariable, true));
          conflicts.putValue(collidingVariable, message);
        }
      }
    };
    JavaUnresolvableLocalCollisionDetector.visitLocalsCollisions(anchor, name, scope, anchor, visitor);
    for (PsiExpression occurence : occurrences) {
      IntroduceVariableBase.checkInLoopCondition(occurence, conflicts);
    }

    if (conflicts.size() > 0) {
      return myIntroduceVariableBase.reportConflicts(conflicts, myProject, settings);
    } else {
      return true;
    }
  }


  public InputValidator(final IntroduceVariableBase introduceVariableBase,
                        Project project,
                        ExpressionOccurrenceManager occurenceManager) {
    myIntroduceVariableBase = introduceVariableBase;
    myProject = project;
    myOccurenceManager = occurenceManager;
  }
}
