package com.intellij.refactoring.introduceField;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.refactoring.util.occurences.ExpressionOccurenceManager;
import com.intellij.refactoring.util.occurences.OccurenceManager;
import org.jetbrains.annotations.NotNull;

public class IntroduceConstantHandler extends BaseExpressionToFieldHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("introduce.constant.title");

  protected String getHelpID() {
    return HelpID.INTRODUCE_CONSTANT;
  }

  public void invoke(Project project, PsiExpression[] expressions) {
    for (PsiExpression expression : expressions) {
      final PsiFile file = expression.getContainingFile();
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    super.invoke(project, expressions, null);
  }

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    ElementToWorkOn.processElementToWorkOn(editor, file, REFACTORING_NAME, getHelpID(), project, getElementProcessor(project, editor));
  }

  protected boolean invokeImpl(Project project, final PsiLocalVariable localVariable, Editor editor) {
    final LocalToFieldHandler localToFieldHandler = new LocalToFieldHandler(project, true);
    return localToFieldHandler.convertLocalToField(localVariable, editor);
  }


  protected Settings showRefactoringDialog(Project project,
                                           Editor editor,
                                           PsiClass parentClass,
                                           PsiExpression expr,
                                           PsiType type,
                                           PsiExpression[] occurences,
                                           PsiElement anchorElement,
                                           PsiElement anchorElementIfAll) {
    PsiLocalVariable localVariable = null;
    if (expr instanceof PsiReferenceExpression) {
      PsiElement ref = ((PsiReferenceExpression)expr).resolve();
      if (ref instanceof PsiLocalVariable) {
        localVariable = (PsiLocalVariable)ref;
      }
    }

    if (localVariable == null) {
      if (!isStaticFinalInitializer(expr)) {
        String message =
          RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.expression.cannot.be.a.constant.initializer"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, getHelpID());
        return null;
      }
    }
    else {
      final PsiExpression initializer = localVariable.getInitializer();
      if (initializer == null) {
        String message = RefactoringBundle
          .getCannotRefactorMessage(RefactoringBundle.message("variable.does.not.have.an.initializer", localVariable.getName()));
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, getHelpID());
        return null;
      }
      if (!isStaticFinalInitializer(initializer)) {
        String message = RefactoringBundle.getCannotRefactorMessage(
          RefactoringBundle.message("initializer.for.variable.cannot.be.a.constant.initializer", localVariable.getName()));
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, getHelpID());
        return null;
      }
    }

    IntroduceConstantDialog dialog =
      new IntroduceConstantDialog(project, parentClass, expr, localVariable, false, occurences, getParentClass(),
                                  new TypeSelectorManagerImpl(project, type, expr, occurences));
    dialog.show();
    if (!dialog.isOK()) {
      if (occurences.length > 1) {
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      }
      return null;
    }
    return new Settings(dialog.getEnteredName(), dialog.isReplaceAllOccurrences(), true, true,
                        BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, dialog.getFieldVisibility(), localVariable,
                        dialog.getSelectedType(), dialog.isDeleteVariable(), dialog.getDestinationClass(), dialog.isAnnotateAsNonNls(),
                        dialog.introduceEnumConstant());
  }


  protected String getRefactoringName() {
    return REFACTORING_NAME;
  }

  private boolean isStaticFinalInitializer(PsiExpression expr) {
    PsiClass parentClass = getParentClass(expr);
    if (parentClass == null) return true;
    IsStaticFinalInitializerExpression visitor = new IsStaticFinalInitializerExpression(parentClass, expr);
    expr.accept(visitor);
    return visitor.isStaticFinalInitializer();
  }

  protected OccurenceManager createOccurenceManager(final PsiExpression selectedExpr, final PsiClass parentClass) {
    return new ExpressionOccurenceManager(selectedExpr, parentClass, null);
  }

  private static class IsStaticFinalInitializerExpression extends ClassMemberReferencesVisitor {
    private boolean myIsStaticFinalInitializer = true;
    private final PsiExpression myInitializer;

    public IsStaticFinalInitializerExpression(PsiClass aClass, PsiExpression initializer) {
      super(aClass);
      myInitializer = initializer;
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      final PsiElement psiElement = expression.resolve();
      if ((psiElement instanceof PsiLocalVariable || psiElement instanceof PsiParameter) &&
          !PsiTreeUtil.isAncestor(myInitializer, psiElement, false)) {
        myIsStaticFinalInitializer = false;
      }
      else {
        super.visitReferenceExpression(expression);
      }
    }


    protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
      myIsStaticFinalInitializer = classMember.hasModifierProperty(PsiModifier.STATIC);
    }

    @Override
    public void visitElement(PsiElement element) {
      if (!myIsStaticFinalInitializer) return;
      super.visitElement(element);
    }

    public boolean isStaticFinalInitializer() {
      return myIsStaticFinalInitializer;
    }
  }

  public PsiClass getParentClass(PsiExpression initializerExpression) {
    final PsiType type = initializerExpression.getType();

    if (type != null && PsiUtil.isConstantExpression(initializerExpression)) {
      if (type instanceof PsiPrimitiveType ||
          PsiType.getJavaLangString(initializerExpression.getManager(), initializerExpression.getResolveScope()).equals(type)) {
        return super.getParentClass(initializerExpression);
      }
    }

    PsiElement parent = initializerExpression.getUserData(ElementToWorkOn.PARENT);
    if (parent == null) parent = initializerExpression;
    PsiClass aClass = PsiTreeUtil.getParentOfType(parent, PsiClass.class);
    while (aClass != null) {
      if (aClass.hasModifierProperty(PsiModifier.STATIC)) return aClass;
      if (aClass.getParent() instanceof PsiJavaFile) return aClass;
      aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
    }
    return null;
  }

  protected boolean validClass(PsiClass parentClass, Editor editor) {
    return true;
  }

  protected boolean isStaticField() {
    return true;
  }
}
