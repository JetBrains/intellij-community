package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.util.Consumer;

import java.util.Arrays;
import java.util.List;

public class HighlightExceptionsHandler extends HighlightUsagesHandlerBase<PsiClass> {
  private final PsiElement myTarget;
  private final PsiClassType[] myClassTypes;
  private final PsiElement myPlace;
  private final Condition<PsiType> myTypeFilter;

  public HighlightExceptionsHandler(final Editor editor, final PsiFile file, final PsiElement target, final PsiClassType[] classTypes,
                                    final PsiElement place, final Condition<PsiType> typeFilter) {
    super(editor, file);
    myTarget = target;
    myClassTypes = classTypes;
    myPlace = place;
    myTypeFilter = typeFilter;
  }

  public List<PsiClass> getTargets() {
    return ChooseClassAndDoHighlightRunnable.resolveClasses(myClassTypes);
  }

  protected void selectTargets(final List<PsiClass> targets, final Consumer<List<PsiClass>> selectionConsumer) {
    new ChooseClassAndDoHighlightRunnable(myClassTypes, myEditor, CodeInsightBundle.message("highlight.exceptions.thrown.chooser.title")) {
      protected void selected(PsiClass... classes) {
        selectionConsumer.consume(Arrays.asList(classes));
      }
    }.run();
  }

  public void computeUsages(final List<PsiClass> targets) {
    final Project project = myEditor.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

    addOccurrence(myTarget);
    for (PsiClass aClass : targets) {
      addExceptionThrownPlaces(factory.createType(aClass));
    }
    buildStatusText(LangBundle.message("java.terms.exception"), myReadUsages.size()-1 /* exclude target */);
  }

  private void addExceptionThrownPlaces(final PsiType type) {
    if (type instanceof PsiClassType) {
      myPlace.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
          visitElement(expression);
        }

        @Override public void visitThrowStatement(PsiThrowStatement statement) {
          super.visitThrowStatement(statement);
          PsiClassType actualType = ExceptionUtil.getUnhandledException(statement, myPlace);
          if (actualType != null && type.isAssignableFrom(actualType) && myTypeFilter.value(actualType)) {
            PsiExpression psiExpression = statement.getException();
            if (psiExpression instanceof PsiReferenceExpression) {
              addOccurrence(psiExpression);
            }
            else if (psiExpression instanceof PsiNewExpression) {
              PsiJavaCodeReferenceElement ref = ((PsiNewExpression)psiExpression).getClassReference();
              addOccurrence(ref);
            }
            else {
              addOccurrence(statement.getException());
            }
          }
        }

        @Override public void visitMethodCallExpression(PsiMethodCallExpression expression) {
          super.visitMethodCallExpression(expression);
          PsiReference reference = expression.getMethodExpression().getReference();
          if (reference == null) return;
          List<PsiClassType> exceptionTypes = ExceptionUtil.getUnhandledExceptions(expression, myPlace);
          for (final PsiClassType actualType : exceptionTypes) {
            if (type.isAssignableFrom(actualType) && myTypeFilter.value(actualType)) {
              addOccurrence(expression.getMethodExpression());
              break;
            }
          }
        }

        @Override public void visitNewExpression(PsiNewExpression expression) {
          super.visitNewExpression(expression);
          PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
          if (classReference == null) return;
          List<PsiClassType> exceptionTypes = ExceptionUtil.getUnhandledExceptions(expression, myPlace);
          for (PsiClassType actualType : exceptionTypes) {
            if (type.isAssignableFrom(actualType) && myTypeFilter.value(actualType)) {
              addOccurrence(classReference);
              break;
            }
          }
        }
      });
    }
  }
}
