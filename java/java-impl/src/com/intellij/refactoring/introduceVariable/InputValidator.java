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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.JavaUnresolvableLocalCollisionDetector;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;

public class InputValidator implements IntroduceVariableBase.Validator {
  private final Project myProject;
  private final PsiElement myAnchorStatementIfAll;
  private final PsiElement myAnchorStatement;
  private final ExpressionOccurrenceManager myOccurenceManager;
  private final IntroduceVariableBase myIntroduceVariableBase;

  public boolean isOK(IntroduceVariableSettings settings) {
    String name = settings.getEnteredName();
    final PsiElement anchor;
    final boolean replaceAllOccurrences = settings.isReplaceAllOccurrences();
    if (replaceAllOccurrences) {
      anchor = myAnchorStatementIfAll;
    } else {
      anchor = myAnchorStatement;
    }
    final PsiElement scope = anchor.getParent();
    if(scope == null) return true;
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    final HashSet<PsiVariable> reportedVariables = new HashSet<>();
    JavaUnresolvableLocalCollisionDetector.CollidingVariableVisitor visitor = new JavaUnresolvableLocalCollisionDetector.CollidingVariableVisitor() {
      public void visitCollidingElement(PsiVariable collidingVariable) {
        if (!reportedVariables.contains(collidingVariable)) {
          reportedVariables.add(collidingVariable);
          String message = RefactoringBundle.message("introduced.variable.will.conflict.with.0", RefactoringUIUtil.getDescription(collidingVariable, true));
          conflicts.putValue(collidingVariable, message);
        }
      }
    };
    JavaUnresolvableLocalCollisionDetector.visitLocalsCollisions(anchor, name, scope, anchor, visitor);
    if (replaceAllOccurrences) {
      final PsiExpression[] occurences = myOccurenceManager.getOccurrences();
      for (PsiExpression occurence : occurences) {
        IntroduceVariableBase.checkInLoopCondition(occurence, conflicts);
      }
    } else {
      IntroduceVariableBase.checkInLoopCondition(myOccurenceManager.getMainOccurence(), conflicts);
    }

    if (conflicts.size() > 0) {
      return myIntroduceVariableBase.reportConflicts(conflicts, myProject, settings);
    } else {
      return true;
    }
  }


  public InputValidator(final IntroduceVariableBase introduceVariableBase,
                        Project project,
                        PsiElement anchorStatementIfAll,
                        PsiElement anchorStatement,
                        ExpressionOccurrenceManager occurenceManager) {
    myIntroduceVariableBase = introduceVariableBase;
    myProject = project;
    myAnchorStatementIfAll = anchorStatementIfAll;
    myAnchorStatement = anchorStatement;
    myOccurenceManager = occurenceManager;
  }
}
