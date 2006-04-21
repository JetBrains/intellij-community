package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Mike
 */
public class CreateMethodFromUsageAction extends CreateFromUsageBaseAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodFromUsageAction");

  private PsiMethodCallExpression myMethodCall;

  public CreateMethodFromUsageAction(PsiMethodCallExpression methodCall) {
    myMethodCall = methodCall;
  }

  protected boolean isAvailableImpl(int offset) {
    PsiReferenceExpression ref = myMethodCall.getMethodExpression();
    String name = ref.getReferenceName();

    if (name == null || !ref.getManager().getNameHelper().isIdentifier(name)) return false;

    setText(QuickFixBundle.message("create.method.from.usage.text", name));
    return true;
  }

  protected PsiElement getElement() {
    if (!myMethodCall.isValid() || !myMethodCall.getManager().isInProject(myMethodCall)) return null;
    return myMethodCall;
  }

  protected void invokeImpl(PsiClass targetClass) {
    PsiManager psiManager = myMethodCall.getManager();
    final Project project = psiManager.getProject();
    PsiReferenceExpression ref = myMethodCall.getMethodExpression();

    if (isValidElement(myMethodCall)) {
      return;
    }

    PsiClass parentClass = PsiTreeUtil.getParentOfType(myMethodCall, PsiClass.class);
    PsiMember enclosingContext = PsiTreeUtil.getParentOfType(myMethodCall,
      PsiMethod.class,
      PsiField.class,
      PsiClassInitializer.class);

    if (targetClass == null) {
      return;
    }

    final PsiFile targetFile = targetClass.getContainingFile();

    String methodName = ref.getReferenceName();

    try {
      PsiElementFactory factory = psiManager.getElementFactory();

      PsiMethod method = factory.createMethod(methodName, PsiType.VOID);

      if (targetClass.equals(parentClass)) {
        method = (PsiMethod) targetClass.addAfter(method, enclosingContext);
      } else {
        PsiElement anchor = enclosingContext;

        while (anchor != null && anchor.getParent() != null && !anchor.getParent().equals(targetClass)) {
          anchor = anchor.getParent();
        }

        if (anchor != null && anchor.getParent() == null) anchor = null;

        if (anchor != null) {
          method = (PsiMethod) targetClass.addAfter(method, anchor);
        } else {
          method = (PsiMethod) targetClass.add(method);
        }
      }

      setupVisibility(parentClass, targetClass, method.getModifierList());

      if (shouldCreateStaticMember(myMethodCall.getMethodExpression(), enclosingContext, targetClass) && !targetClass.isInterface()) {
        method.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
      }

      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      final Document document = documentManager.getDocument(targetFile);
      final PsiFile psiFile = myMethodCall.getContainingFile();

      RangeMarker rangeMarker = document.createRangeMarker(myMethodCall.getTextRange());
      method = CodeInsightUtil.forcePsiPosprocessAndRestoreElement(method);
      myMethodCall = CodeInsightUtil.findElementInRange(psiFile, rangeMarker.getStartOffset(), rangeMarker.getEndOffset(), PsiMethodCallExpression.class);
      TemplateBuilder builder = new TemplateBuilder(method);

      targetClass = (PsiClass)method.getParent();
      parentClass = PsiTreeUtil.getParentOfType(myMethodCall, PsiClass.class);
      final ExpectedTypeInfo[] expectedTypes = CreateFromUsageUtils.guessExpectedTypes(myMethodCall, true);
      final PsiSubstitutor substitutor = getTargetSubstitutor(myMethodCall);
      final PsiElement context = PsiTreeUtil.getParentOfType(myMethodCall, PsiClass.class, PsiMethod.class);

      CreateFromUsageUtils.setupMethodParameters(method, builder, myMethodCall.getArgumentList(), substitutor);
      new GuessTypeParameters(factory).setupTypeElement(method.getReturnTypeElement(), expectedTypes, substitutor, builder, context, targetClass);
      PsiCodeBlock body = method.getBody();
      assert body != null;
      if (!targetClass.isInterface()) {
        builder.setEndVariableAfter(body.getLBrace());
      } else {
        body.delete();
        builder.setEndVariableAfter(method);
      }

      rangeMarker = document.createRangeMarker(method.getTextRange());
      method = CodeInsightUtil.forcePsiPosprocessAndRestoreElement(method);
      final Editor newEditor = positionCursor(project, targetFile, method);
      Template template = builder.buildTemplate();
      newEditor.getCaretModel().moveToOffset(rangeMarker.getStartOffset());
      newEditor.getDocument().deleteString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset());

      if (!targetClass.isInterface()) {
        startTemplate(newEditor, template, project, new TemplateEditingAdapter() {
          public void templateFinished(Template template) {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                PsiDocumentManager.getInstance(project).commitDocument(newEditor.getDocument());
                final int offset = newEditor.getCaretModel().getOffset();
                PsiMethod method = PsiTreeUtil.findElementOfClassAtOffset(targetFile, offset, PsiMethod.class, false);

                if (method != null) {
                  try {
                    CreateFromUsageUtils.setupMethodBody(method);
                  } catch (IncorrectOperationException e) {
                    LOG.error(e);
                  }

                  CreateFromUsageUtils.setupEditor(method, newEditor);
                }
              }
            });
          }
        });
      } else {
        startTemplate(newEditor, template, project);
      }
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  protected boolean isValidElement(PsiElement element) {
    PsiMethodCallExpression callExpression = (PsiMethodCallExpression) element;
    PsiReferenceExpression referenceExpression = callExpression.getMethodExpression();

    return CreateFromUsageUtils.isValidMethodReference(referenceExpression, callExpression);
  }

  public String getFamilyName() {
    return QuickFixBundle.message("create.method.from.usage.family");
  }
}
