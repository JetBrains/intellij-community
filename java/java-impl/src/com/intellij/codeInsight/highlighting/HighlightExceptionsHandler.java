/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

class HighlightExceptionsHandler extends HighlightUsagesHandlerBase<PsiClass> {
  private final PsiElement myTarget;
  private final PsiClassType[] myClassTypes;
  private final PsiElement myPlace;
  private final PsiElement myOtherPlace;
  private final Predicate<? super PsiType> myTypeFilter;

  HighlightExceptionsHandler(@NotNull Editor editor,
                             @NotNull PsiFile file,
                             @NotNull PsiElement target,
                             PsiClassType @NotNull [] classTypes,
                             @NotNull PsiElement place,
                             PsiElement otherPlace,
                             @NotNull Predicate<? super PsiType> typeFilter) {
    super(editor, file);
    myTarget = target;
    myClassTypes = classTypes;
    myPlace = place;
    myOtherPlace = otherPlace;
    myTypeFilter = typeFilter;
  }

  @Override
  public @NotNull List<PsiClass> getTargets() {
    return ChooseClassAndDoHighlightRunnable.resolveClasses(myClassTypes);
  }

  @Override
  protected void selectTargets(final @NotNull List<? extends PsiClass> targets, final @NotNull Consumer<? super List<? extends PsiClass>> selectionConsumer) {
    new ChooseClassAndDoHighlightRunnable(myClassTypes, myEditor, JavaBundle.message("highlight.exceptions.thrown.chooser.title")) {
      @Override
      protected void selected(PsiClass @NotNull ... classes) {
        selectionConsumer.consume(Arrays.asList(classes));
      }
    }.run();
  }

  @Override
  public void computeUsages(final @NotNull List<? extends PsiClass> targets) {
    addUsage(myTarget);

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(myFile.getProject());
    for (PsiClass aClass : targets) {
      addExceptionThrowPlaces(factory.createType(aClass), myPlace);
      if (myOtherPlace != null) {
        addExceptionThrowPlaces(factory.createType(aClass), myOtherPlace);
      }
    }

    buildStatusText(JavaBundle.message("java.terms.exception"), myReadUsages.size() - 1 /* exclude target */);
  }

  private void addExceptionThrowPlaces(@NotNull PsiClassType type, @NotNull PsiElement place) {
    place.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitElement(expression);
      }

      @Override
      public void visitThrowStatement(PsiThrowStatement statement) {
        super.visitThrowStatement(statement);
        List<PsiClassType> actualTypes = ExceptionUtil.getUnhandledExceptions(statement, place);
        for (PsiClassType actualType : actualTypes) {
          if (actualType != null && type.isAssignableFrom(actualType) && myTypeFilter.test(actualType)) {
            PsiExpression psiExpression = statement.getException();
            if (psiExpression instanceof PsiReferenceExpression) {
              addUsage(psiExpression);
            }
            else if (psiExpression instanceof PsiNewExpression) {
              PsiJavaCodeReferenceElement ref = ((PsiNewExpression)psiExpression).getClassReference();
              if (ref != null) {
                addUsage(ref);
              }
            }
            else {
              PsiExpression exception = statement.getException();
              if (exception != null) {
                addUsage(exception);
              }
            }
          }
        }
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        PsiReference reference = expression.getMethodExpression().getReference();
        if (reference != null) {
          List<PsiClassType> exceptionTypes = ExceptionUtil.getUnhandledExceptions(expression, place);
          for (final PsiClassType actualType : exceptionTypes) {
            if (type.isAssignableFrom(actualType) && myTypeFilter.test(actualType)) {
              addUsage(expression.getMethodExpression());
              break;
            }
          }
        }
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        super.visitNewExpression(expression);
        PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
        if (classReference != null) {
          List<PsiClassType> exceptionTypes = ExceptionUtil.getUnhandledExceptions(expression, place);
          for (PsiClassType actualType : exceptionTypes) {
            if (type.isAssignableFrom(actualType) && myTypeFilter.test(actualType)) {
              addUsage(classReference);
              break;
            }
          }
        }
      }

      @Override
      public void visitResourceExpression(PsiResourceExpression expression) {
        super.visitResourceExpression(expression);
        List<PsiClassType> exceptionTypes = ExceptionUtil.getUnhandledCloserExceptions(expression, place);
        for (PsiClassType actualType : exceptionTypes) {
          if (type.isAssignableFrom(actualType) && myTypeFilter.test(actualType)) {
            addUsage(expression);
            break;
          }
        }
      }

      @Override
      public void visitResourceVariable(PsiResourceVariable variable) {
        super.visitResourceVariable(variable);
        List<PsiClassType> exceptionTypes = ExceptionUtil.getUnhandledCloserExceptions(variable, place);
        for (PsiClassType actualType : exceptionTypes) {
          if (type.isAssignableFrom(actualType) && myTypeFilter.test(actualType)) {
            PsiIdentifier name = variable.getNameIdentifier();
            if (name != null) {
              addUsage(name);
              break;
            }
          }
        }
      }
    });
  }

  private void addUsage(@NotNull PsiElement element) {
    addOccurrence(element);
  }
}