/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RemoveUnusedVariableUtil {
  public static final int MAKE_STATEMENT = 1;
  public static final int DELETE_ALL = 2;
  public static final int CANCEL = 0;
  private static final Set<String> ourSideEffectFreeClasses = new THashSet<String>();
  static {
    ourSideEffectFreeClasses.add(Object.class.getName());
    ourSideEffectFreeClasses.add(Short.class.getName());
    ourSideEffectFreeClasses.add(Character.class.getName());
    ourSideEffectFreeClasses.add(Byte.class.getName());
    ourSideEffectFreeClasses.add(Integer.class.getName());
    ourSideEffectFreeClasses.add(Long.class.getName());
    ourSideEffectFreeClasses.add(Float.class.getName());
    ourSideEffectFreeClasses.add(Double.class.getName());
    ourSideEffectFreeClasses.add(String.class.getName());
    ourSideEffectFreeClasses.add(StringBuffer.class.getName());
    ourSideEffectFreeClasses.add(Boolean.class.getName());

    ourSideEffectFreeClasses.add(ArrayList.class.getName());
    ourSideEffectFreeClasses.add(Date.class.getName());
    ourSideEffectFreeClasses.add(HashMap.class.getName());
    ourSideEffectFreeClasses.add(HashSet.class.getName());
    ourSideEffectFreeClasses.add(Hashtable.class.getName());
    ourSideEffectFreeClasses.add(LinkedHashMap.class.getName());
    ourSideEffectFreeClasses.add(LinkedHashSet.class.getName());
    ourSideEffectFreeClasses.add(LinkedList.class.getName());
    ourSideEffectFreeClasses.add(Stack.class.getName());
    ourSideEffectFreeClasses.add(TreeMap.class.getName());
    ourSideEffectFreeClasses.add(TreeSet.class.getName());
    ourSideEffectFreeClasses.add(Vector.class.getName());
    ourSideEffectFreeClasses.add(WeakHashMap.class.getName());
  }

  static boolean isSideEffectFreeConstructor(PsiNewExpression newExpression) {
    PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    PsiClass aClass = classReference == null ? null : (PsiClass)classReference.resolve();
    String qualifiedName = aClass == null ? null : aClass.getQualifiedName();
    if (qualifiedName == null) return false;
    if (ourSideEffectFreeClasses.contains(qualifiedName)) return true;

    PsiFile file = aClass.getContainingFile();
    PsiDirectory directory = file.getContainingDirectory();
    PsiPackage classPackage = JavaDirectoryService.getInstance().getPackage(directory);
    String packageName = classPackage == null ? null : classPackage.getQualifiedName();

    // all Throwable descendants from java.lang are side effects free
    if ("java.lang".equals(packageName) || "java.io".equals(packageName)) {
      PsiClass throwableClass = JavaPsiFacade.getInstance(aClass.getProject()).findClass("java.lang.Throwable", aClass.getResolveScope());
      if (throwableClass != null && InheritanceUtil.isInheritorOrSelf(aClass, throwableClass, true)) {
        return true;
      }
    }
    return false;
  }

  public static boolean checkSideEffects(PsiElement element, PsiVariable variable, List<PsiElement> sideEffects) {
  if (sideEffects == null || element == null) return false;
  if (element instanceof PsiMethodCallExpression) {
    final PsiMethod psiMethod = ((PsiMethodCallExpression)element).resolveMethod();
    if (psiMethod == null || !PropertyUtil.isSimpleGetter(psiMethod) && !PropertyUtil.isSimpleSetter(psiMethod)) {
      sideEffects.add(element);
      return true;
    }
  }
  if (element instanceof PsiNewExpression) {
    PsiNewExpression newExpression = (PsiNewExpression)element;
    if (newExpression.getArrayDimensions().length == 0
        && newExpression.getArrayInitializer() == null
        && !isSideEffectFreeConstructor(newExpression)) {
      sideEffects.add(element);
      return true;
    }
  }
  if (element instanceof PsiAssignmentExpression
      && !(((PsiAssignmentExpression)element).getLExpression() instanceof PsiReferenceExpression
           && ((PsiReferenceExpression)((PsiAssignmentExpression)element).getLExpression()).resolve() == variable)) {
    sideEffects.add(element);
    return true;
  }
  PsiElement[] children = element.getChildren();

    for (PsiElement child : children) {
      checkSideEffects(child, variable, sideEffects);
    }
  return !sideEffects.isEmpty();
}

  static PsiElement replaceElementWithExpression(PsiExpression expression,
                                                 PsiElementFactory factory,
                                                 PsiElement element) throws IncorrectOperationException {
    PsiElement elementToReplace = element;
    PsiElement expressionToReplaceWith = expression;
    if (element.getParent() instanceof PsiExpressionStatement) {
      elementToReplace = element.getParent();
      expressionToReplaceWith =
      factory.createStatementFromText((expression == null ? "" : expression.getText()) + ";", null);
    }
    else if (element.getParent() instanceof PsiDeclarationStatement) {
      expressionToReplaceWith =
      factory.createStatementFromText((expression == null ? "" : expression.getText()) + ";", null);
    }
    return elementToReplace.replace(expressionToReplaceWith);
  }

  static PsiElement createStatementIfNeeded(PsiExpression expression,
                                            PsiElementFactory factory,
                                            PsiElement element) throws IncorrectOperationException {
    // if element used in expression, subexpression will do
    if (!(element.getParent() instanceof PsiExpressionStatement) &&
        !(element.getParent() instanceof PsiDeclarationStatement)) {
      return expression;
    }
    return factory.createStatementFromText((expression == null ? "" : expression.getText()) + ";", null);
  }

  static void deleteWholeStatement(PsiElement element, PsiElementFactory factory)
    throws IncorrectOperationException {
    // just delete it altogether
    if (element.getParent() instanceof PsiExpressionStatement) {
      PsiExpressionStatement parent = (PsiExpressionStatement)element.getParent();
      if (parent.getParent() instanceof PsiCodeBlock) {
        parent.delete();
      }
      else {
        // replace with empty statement (to handle with 'if (..) i=0;' )
        parent.replace(createStatementIfNeeded(null, factory, element));
      }
    }
    else {
      element.delete();
    }
  }

  static void deleteReferences(PsiVariable variable, List<PsiElement> references, int mode) throws IncorrectOperationException {
    for (PsiElement expression : references) {
      processUsage(expression, variable, null, mode);
    }
  }

  static void collectReferences(@NotNull PsiElement context, final PsiVariable variable, final List<PsiElement> references) {
    context.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.resolve() == variable) references.add(expression);
        super.visitReferenceExpression(expression);
      }
    });
  }

  /**
   * @param sideEffects if null, delete usages, otherwise collect side effects
   * @return true if there are at least one unrecoverable side effect found, false if no side effects,
   *         null if read usage found (may happen if interval between fix creation in invoke() call was long enough)
   * @throws com.intellij.util.IncorrectOperationException
   */
  static Boolean processUsage(PsiElement element, PsiVariable variable, List<PsiElement> sideEffects, int deleteMode)
    throws IncorrectOperationException {
    if (!element.isValid()) return null;
    PsiElementFactory factory = JavaPsiFacade.getInstance(variable.getProject()).getElementFactory();
    while (element != null) {
      if (element instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression expression = (PsiAssignmentExpression)element;
        PsiExpression lExpression = expression.getLExpression();
        // there should not be read access to the variable, otherwise it is not unused
        if (!(lExpression instanceof PsiReferenceExpression) || variable != ((PsiReferenceExpression)lExpression).resolve()) {
          return null;
        }
        PsiExpression rExpression = expression.getRExpression();
        rExpression = PsiUtil.deparenthesizeExpression(rExpression);
        if (rExpression == null) return true;
        // replace assignment with expression and resimplify
        boolean sideEffectFound = checkSideEffects(rExpression, variable, sideEffects);
        if (!(element.getParent() instanceof PsiExpressionStatement) || PsiUtil.isStatement(rExpression)) {
          if (deleteMode == MAKE_STATEMENT ||
              deleteMode == DELETE_ALL && !(element.getParent() instanceof PsiExpressionStatement)) {
            element = replaceElementWithExpression(rExpression, factory, element);
            while (element.getParent() instanceof PsiParenthesizedExpression) {
              element = element.getParent().replace(element);
            }
            List<PsiElement> references = new ArrayList<PsiElement>();
            collectReferences(element, variable, references);
            deleteReferences(variable, references, deleteMode);
          }
          else if (deleteMode == DELETE_ALL) {
            deleteWholeStatement(element, factory);
          }
          return true;
        }
        else {
          if (deleteMode != CANCEL) {
            deleteWholeStatement(element, factory);
          }
          return !sideEffectFound;
        }
      }
      else if (element instanceof PsiExpressionStatement && deleteMode != CANCEL) {
        element.delete();
        break;
      }
      else if (element instanceof PsiVariable && element == variable) {
        PsiExpression expression = variable.getInitializer();
        if (expression != null) {
          expression = PsiUtil.deparenthesizeExpression(expression);
        }
        boolean sideEffectsFound = checkSideEffects(expression, variable, sideEffects);
        if (expression != null && PsiUtil.isStatement(expression) && variable instanceof PsiLocalVariable
            &&
            !(variable.getParent() instanceof PsiDeclarationStatement &&
              ((PsiDeclarationStatement)variable.getParent()).getDeclaredElements().length > 1)) {
          if (deleteMode == MAKE_STATEMENT) {
            element = element.replace(createStatementIfNeeded(expression, factory, element));
            List<PsiElement> references = new ArrayList<PsiElement>();
            collectReferences(element, variable, references);
            deleteReferences(variable, references, deleteMode);
          }
          else if (deleteMode == DELETE_ALL) {
            element.delete();
          }
          return true;
        }
        else {
          if (deleteMode != CANCEL) {
            if (element instanceof PsiField) {
              ((PsiField)element).normalizeDeclaration();
            }
            element.delete();
          }
          return !sideEffectsFound;
        }
      }
      element = element.getParent();
    }
    return true;
  }
}
