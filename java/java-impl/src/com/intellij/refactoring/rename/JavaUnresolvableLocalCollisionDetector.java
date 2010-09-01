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
package com.intellij.refactoring.rename;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;

import java.util.List;

public class JavaUnresolvableLocalCollisionDetector {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.JavaUnresolvableLocalCollisionDetector");

  private JavaUnresolvableLocalCollisionDetector() {
  }

  public static void findCollisions(final PsiElement element, final String newName, final List<UsageInfo> result) {
    if (!(element instanceof PsiLocalVariable || element instanceof PsiParameter)) {
      return;
    }


    PsiElement scope;
    PsiElement anchor = null;
    if (element instanceof PsiLocalVariable) {
      scope = RefactoringUtil.getVariableScope((PsiLocalVariable)element);
      if (!(element instanceof ImplicitVariable)) {
        anchor = element.getParent();
      }
    }
    else {
      // element is a PsiParameter
      scope = ((PsiParameter)element).getDeclarationScope();
    }
    LOG.assertTrue(scope != null, element.getClass().getName());

    final CollidingVariableVisitor collidingNameVisitor = new CollidingVariableVisitor() {
      public void visitCollidingElement(PsiVariable collidingVariable) {
        if (collidingVariable.equals(element)) return;
        LocalHidesRenamedLocalUsageInfo collision = new LocalHidesRenamedLocalUsageInfo(element, collidingVariable);
        result.add(collision);
      }
    };

    visitLocalsCollisions(element, newName, scope, anchor, collidingNameVisitor);


    /*PsiElement place = scope.getLastChild();
    PsiResolveHelper helper = place.getManager().getResolveHelper();
    PsiVariable refVar = helper.resolveReferencedVariable(newName, place, null);

    if (refVar != null) {
      LocalHidesRenamedLocalUsageInfo collision = new LocalHidesRenamedLocalUsageInfo(element, refVar);
      result.add(collision);
    }*/
  }

  public static void visitLocalsCollisions(PsiElement element, final String newName,
                                           PsiElement scope,
                                           PsiElement place,
                                           final CollidingVariableVisitor collidingNameVisitor) {
    if (scope == null) return;
    visitDownstreamCollisions(scope, place, newName, collidingNameVisitor);
    visitUpstreamLocalCollisions(element, scope, newName, collidingNameVisitor);
  }

  private static void visitDownstreamCollisions(PsiElement scope, PsiElement place, final String newName,
                                                final CollidingVariableVisitor collidingNameVisitor
                                               ) {
    ConflictingLocalVariablesVisitor collector =
      new ConflictingLocalVariablesVisitor(newName, collidingNameVisitor);
    if (place == null) {
      scope.accept(collector);
    }
    else {
      LOG.assertTrue(place.getParent() == scope);
      for (PsiElement sibling = place; sibling != null; sibling = sibling.getNextSibling()) {
        sibling.accept(collector);
      }

    }
  }

  public interface CollidingVariableVisitor {
    void visitCollidingElement(PsiVariable collidingVariable);
  }

  private static void visitUpstreamLocalCollisions(PsiElement element, PsiElement scope,
                                                  String newName,
                                                  final CollidingVariableVisitor collidingNameVisitor) {
    final PsiVariable collidingVariable =
      JavaPsiFacade.getInstance(scope.getProject()).getResolveHelper().resolveAccessibleReferencedVariable(newName, scope);
    if (collidingVariable instanceof PsiLocalVariable || collidingVariable instanceof PsiParameter) {
      final PsiElement commonParent = PsiTreeUtil.findCommonParent(element, collidingVariable);
      if (commonParent != null) {
        PsiElement current = element;
        while (current != null && current != commonParent) {
          if (current instanceof PsiMethod || current instanceof PsiClass) {
            return;
          }
          current = current.getParent();
        }
      }
      collidingNameVisitor.visitCollidingElement(collidingVariable);
    }
  }

  public static class ConflictingLocalVariablesVisitor extends JavaRecursiveElementWalkingVisitor {
    protected final String myName;
    protected CollidingVariableVisitor myCollidingNameVisitor;

    public ConflictingLocalVariablesVisitor(String newName, CollidingVariableVisitor collidingNameVisitor) {
      myName = newName;
      myCollidingNameVisitor = collidingNameVisitor;
    }

    @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitElement(expression);
    }

    @Override
    public void visitField(PsiField field) {
      if (myName.equals(field.getName())) {
        myCollidingNameVisitor.visitCollidingElement(field);
      }
    }

    @Override public void visitVariable(PsiVariable variable) {
      if (myName.equals(variable.getName())) {
        myCollidingNameVisitor.visitCollidingElement(variable);
      }
    }
  }



}
