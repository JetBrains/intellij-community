package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: ven
 * Date: May 12, 2003
 * Time: 6:41:19 PM
 * To change this template use Options | File Templates.
 */
public abstract class CreateConstructorFromThisOrSuperAction extends CreateFromUsageBaseAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateConstructorFromThisOrSuperAction");

  protected PsiMethodCallExpression myMethodCall;

  public CreateConstructorFromThisOrSuperAction(PsiMethodCallExpression methodCall) {
    myMethodCall = methodCall;
  }

  abstract protected @NonNls String getSyntheticMethodName ();

  protected boolean isAvailableImpl(int offset) {
    PsiReferenceExpression ref = myMethodCall.getMethodExpression();
    if (!ref.getText().equals(getSyntheticMethodName())) return false;

    PsiMethod method = PsiTreeUtil.getParentOfType(myMethodCall, PsiMethod.class);
    if (method == null || !method.isConstructor()) return false;

    PsiClass[] targetClasses = getTargetClasses(myMethodCall);
    LOG.assertTrue(targetClasses.length == 1);

    if (shouldShowTag(offset, ref.getReferenceNameElement(), myMethodCall)) {
      setText(QuickFixBundle.message("create.constructor.text", targetClasses[0].getName()));
      return true;
    }

    return false;
  }

  protected void invokeImpl(PsiClass targetClass) {
    PsiManager psiManager = myMethodCall.getManager();
    final PsiFile callSite = myMethodCall.getContainingFile();
    final Project project = psiManager.getProject();
    PsiElementFactory elementFactory = psiManager.getElementFactory();

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

    try {
      PsiMethod constructor = elementFactory.createConstructor();
      constructor = (PsiMethod) targetClass.add(constructor);

      final TemplateBuilder templateBuilder = new TemplateBuilder(constructor);
      CreateFromUsageUtils.setupMethodParameters(constructor, templateBuilder, myMethodCall.getArgumentList(), getTargetSubstitutor(myMethodCall));

      final PsiFile psiFile = myMethodCall.getContainingFile();

      templateBuilder.setEndVariableAfter(constructor.getBody().getLBrace());
      final RangeMarker rangeMarker = psiFile.getViewProvider().getDocument().createRangeMarker(myMethodCall.getTextRange());

      constructor = CodeInsightUtil.forcePsiPostprocessAndRestoreElement(constructor);

      targetClass = constructor.getContainingClass();
      myMethodCall =
        CodeInsightUtil.findElementInRange(psiFile, rangeMarker.getStartOffset(), rangeMarker.getEndOffset(), myMethodCall.getClass());


      Template template = templateBuilder.buildTemplate();
      final Editor editor = positionCursor(project, targetClass.getContainingFile(), targetClass);
      TextRange textRange = constructor.getTextRange();
      final PsiFile file = targetClass.getContainingFile();
      editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
      editor.getCaretModel().moveToOffset(textRange.getStartOffset());

      startTemplate(editor, template, project, new TemplateEditingAdapter() {
        public void templateFinished(Template template) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
                final int offset = editor.getCaretModel().getOffset();
                PsiMethod constructor = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiMethod.class, false);
                CreateFromUsageUtils.setupMethodBody(constructor);
                CreateFromUsageUtils.setupEditor(constructor, editor);

                UndoManager.getInstance(callSite.getProject()).markDocumentForUndo(callSite);
              } catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
          });
        }
      });
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  protected boolean isValidElement(PsiElement element) {
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression) element;
    PsiMethod method = (PsiMethod) methodCall.getMethodExpression().resolve();
    PsiExpressionList argumentList = methodCall.getArgumentList();
    PsiClass targetClass = getTargetClasses(element)[0];

    return !CreateFromUsageUtils.shouldCreateConstructor(targetClass, argumentList, method);
  }

  protected PsiElement getElement() {
    if (!myMethodCall.isValid() || !myMethodCall.getManager().isInProject(myMethodCall)) return null;
    return myMethodCall;
  }
}
