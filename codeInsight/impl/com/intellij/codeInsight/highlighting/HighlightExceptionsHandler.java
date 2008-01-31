package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.List;

public class HighlightExceptionsHandler implements HighlightUsagesHandlerDelegate {
  public boolean highlightUsages(final Editor editor, final PsiFile file) {
    int offset = TargetElementUtilBase.adjustOffset(editor.getDocument(), editor.getCaretModel().getOffset());
    PsiElement target = file.findElementAt(offset);
    if (target instanceof PsiKeyword) {
      PsiElement parent = target.getParent();
      if (PsiKeyword.TRY.equals(target.getText()) && parent instanceof PsiTryStatement) {
        highlightTry(editor, file, target, (PsiTryStatement) parent);
        return true;
      }
      if (PsiKeyword.CATCH.equals(target.getText()) && parent instanceof PsiCatchSection) {
        highlightCatch(editor, file, target, (PsiCatchSection) parent);
        return true;
      }
      if (PsiKeyword.THROWS.equals(target.getText())) {
        highlightThrows(editor, file, target);
        return true;
      }
    }
    return false;
  }

  private static void highlightTry(final Editor editor, final PsiFile file, final PsiElement target, final PsiTryStatement tryStatement) {
    final PsiClassType[] psiClassTypes = ExceptionUtil.collectUnhandledExceptions(tryStatement.getTryBlock(), tryStatement.getTryBlock());
    chooseExceptionAndHighlight(psiClassTypes, tryStatement.getTryBlock(), target, editor, file, Condition.TRUE);
  }

  private static void highlightCatch(final Editor editor, final PsiFile file, final PsiElement target, final PsiCatchSection catchSection) {
    PsiTryStatement tryStatement = catchSection.getTryStatement();

    final PsiParameter param = catchSection.getParameter();
    if (param == null) return;

    final PsiParameter[] catchBlockParameters = tryStatement.getCatchBlockParameters();

    final PsiClassType[] allThrownExceptions = ExceptionUtil.collectUnhandledExceptions(tryStatement.getTryBlock(),
                                                                                        tryStatement.getTryBlock());
    Condition<PsiType> filter = new Condition<PsiType>() {
      public boolean value(PsiType type) {
        for (PsiParameter parameter : catchBlockParameters) {
          boolean isAssignable = parameter.getType().isAssignableFrom(type);
          if (parameter != param) {
            if (isAssignable) return false;
          }
          else {
            return isAssignable;
          }
        }
        return false;
      }
    };

    ArrayList<PsiClassType> filtered = new ArrayList<PsiClassType>();
    for (PsiClassType type : allThrownExceptions) {
      if (filter.value(type)) filtered.add(type);
    }

    chooseExceptionAndHighlight(filtered.toArray(new PsiClassType[filtered.size()]), tryStatement.getTryBlock(),
                                target, editor, file, filter);
  }

  private static void highlightThrows(final Editor editor, final PsiFile file, final PsiElement target) {
    PsiElement grand = target.getParent().getParent();
    if (!(grand instanceof PsiMethod)) return;
    PsiMethod method = (PsiMethod)grand;
    if (method.getBody() == null) return;

    final PsiClassType[] psiClassTypes = ExceptionUtil.collectUnhandledExceptions(method.getBody(), method.getBody());
    chooseExceptionAndHighlight(psiClassTypes, method.getBody(), target, editor, file, Condition.TRUE);
  }

  public static void chooseExceptionAndHighlight(final PsiClassType[] psiClassTypes,
                                          final PsiElement place, final PsiElement target, final Editor editor,
                                          final PsiFile file, final Condition<PsiType> typeFilter) {
    final Project project = target.getProject();
    final boolean clearHighlights = HighlightUsagesHandler.isClearHighlights(editor, HighlightManager.getInstance(target.getProject()));
    if (psiClassTypes == null || psiClassTypes.length == 0) {
      String text = CodeInsightBundle.message("highlight.exceptions.thrown.notfound");
      HintManager.getInstance().showInformationHint(editor, text);
      return;
    }
    new ChooseClassAndDoHighlightRunnable(psiClassTypes, editor, CodeInsightBundle.message("highlight.exceptions.thrown.chooser.title")) {
      protected void selected(PsiClass... classes) {
        List<PsiReference> refs = new ArrayList<PsiReference>();
        final ArrayList<PsiElement> otherOccurrences = new ArrayList<PsiElement>();
        final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

        for (PsiClass aClass : classes) {
          addExceptionThrownPlaces(refs, otherOccurrences, factory.createType(aClass), place, typeFilter);
        }
        new HighlightUsagesHandler.DoHighlightRunnable(refs, project, target, editor, file, clearHighlights).run();
        HighlightUsagesHandler.highlightOtherOccurrences(otherOccurrences, project, editor, clearHighlights);
      }
    }.run();
  }

  private static void addExceptionThrownPlaces(final List<PsiReference> refs, final List<PsiElement> otherOccurrences, final PsiType type,
                                               final PsiElement block, final Condition<PsiType> typeFilter) {
    if (type instanceof PsiClassType) {
      block.accept(new JavaRecursiveElementVisitor() {
        @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
          visitElement(expression);
        }

        @Override public void visitThrowStatement(PsiThrowStatement statement) {
          super.visitThrowStatement(statement);
          PsiClassType[] exceptionTypes = ExceptionUtil.getUnhandledExceptions(statement, block);
          for (final PsiClassType actualType : exceptionTypes) {
            if (type.isAssignableFrom(actualType) && typeFilter.value(actualType)) {
              PsiExpression psiExpression = statement.getException();
              if (psiExpression instanceof PsiReferenceExpression) {
                PsiReferenceExpression referenceExpression = (PsiReferenceExpression)psiExpression;
                if (!refs.contains(referenceExpression)) refs.add(referenceExpression);
              }
              else if (psiExpression instanceof PsiNewExpression) {
                PsiJavaCodeReferenceElement ref = ((PsiNewExpression)psiExpression).getClassReference();
                if (ref != null && !refs.contains(ref)) refs.add(ref);
              }
              else {
                otherOccurrences.add(statement.getException());
              }
            }
          }
        }

        @Override public void visitMethodCallExpression(PsiMethodCallExpression expression) {
          super.visitMethodCallExpression(expression);
          PsiReference reference = expression.getMethodExpression().getReference();
          if (reference == null || refs.contains(reference)) return;
          PsiClassType[] exceptionTypes = ExceptionUtil.getUnhandledExceptions(expression, block);
          for (final PsiClassType actualType : exceptionTypes) {
            if (type.isAssignableFrom(actualType) && typeFilter.value(actualType)) {
              refs.add(reference);
              break;
            }
          }
        }

        @Override public void visitNewExpression(PsiNewExpression expression) {
          super.visitNewExpression(expression);
          PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
          if (classReference == null || refs.contains(classReference)) return;
          PsiClassType[] exceptionTypes = ExceptionUtil.getUnhandledExceptions(expression, block);
          for (PsiClassType actualType : exceptionTypes) {
            if (type.isAssignableFrom(actualType) && typeFilter.value(actualType)) {
              refs.add(classReference);
              break;
            }
          }
        }
      });
    }
  }
}
