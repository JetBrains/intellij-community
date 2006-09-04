package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author mike
 */
public class CreateClassFromNewAction extends CreateFromUsageBaseAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateClassFromNewAction");
  private final SmartPsiElementPointer myNewExpression;

  public CreateClassFromNewAction(PsiNewExpression newExpression) {
    myNewExpression = SmartPointerManager.getInstance(newExpression.getProject()).createSmartPsiElementPointer(newExpression);
  }

  private PsiNewExpression getNewExpression() {
    return (PsiNewExpression)myNewExpression.getElement();
  }

  protected void invokeImpl(PsiClass targetClass) {
    PsiManager psiManager = getNewExpression().getManager();
    final Project project = psiManager.getProject();
    final PsiElementFactory elementFactory = psiManager.getElementFactory();

    final PsiClass psiClass = CreateFromUsageUtils.createClass(getReferenceElement(getNewExpression()),
                                                               CreateClassKind.CLASS,
                                                               null);
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          try {
            PsiClass aClass = psiClass;
            if (aClass == null) return;

            setupInheritance(getNewExpression(), aClass);
            setupGenericParameters(getNewExpression(), aClass);

            PsiExpressionList argList = getNewExpression().getArgumentList();
            if (argList != null && argList.getExpressions().length > 0) {
              PsiMethod constructor = elementFactory.createConstructor();
              constructor = (PsiMethod) aClass.add(constructor);

              TemplateBuilder templateBuilder = new TemplateBuilder(aClass);
              CreateFromUsageUtils.setupMethodParameters(constructor, templateBuilder, argList, getTargetSubstitutor(getNewExpression()));

              setupSuperCall(aClass, constructor, templateBuilder);

              getReferenceElement(getNewExpression()).bindToElement(aClass);
              aClass = CodeInsightUtil.forcePsiPostprocessAndRestoreElement(aClass);
              Template template = templateBuilder.buildTemplate();

              Editor editor = positionCursor(project, aClass.getContainingFile(), aClass);
              TextRange textRange = aClass.getTextRange();
              editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());

              startTemplate(editor, template, project);
            } else {
              positionCursor(project, aClass.getContainingFile(), aClass);
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    );
  }

  public boolean startInWriteAction() {
    return false;
  }

  public static void setupSuperCall(PsiClass targetClass, PsiMethod constructor, TemplateBuilder templateBuilder)
    throws IncorrectOperationException {
    PsiElementFactory elementFactory = targetClass.getManager().getElementFactory();

    PsiClass superClass = targetClass.getSuperClass();
    if (superClass != null && !"java.lang.Object".equals(superClass.getQualifiedName()) &&
          !"java.lang.Enum".equals(superClass.getQualifiedName())) {
      PsiMethod[] constructors = superClass.getConstructors();
      boolean hasDefaultConstructor = false;

      for (PsiMethod superConstructor : constructors) {
        if (superConstructor.getParameterList().getParameters().length == 0) {
          hasDefaultConstructor = true;
          break;
        }
      }

      if (!hasDefaultConstructor) {
        PsiExpressionStatement statement =
          (PsiExpressionStatement)elementFactory.createStatementFromText("super();", constructor);
        statement = (PsiExpressionStatement)constructor.getBody().add(statement);

        PsiMethodCallExpression call = (PsiMethodCallExpression)statement.getExpression();
        PsiExpressionList argumentList = call.getArgumentList();
        templateBuilder.setEndVariableAfter(argumentList.getFirstChild());
      }
    }

    templateBuilder.setEndVariableAfter(constructor.getBody().getLBrace());
  }

  private void setupGenericParameters(PsiNewExpression expr, PsiClass targetClass) throws IncorrectOperationException {
    PsiJavaCodeReferenceElement ref = expr.getClassReference();
    int numParams = ref.getTypeParameters().length;
    if (numParams == 0) return;
    PsiElementFactory factory = expr.getManager().getElementFactory();
    targetClass.getTypeParameterList().add(factory.createTypeParameterFromText("T", null));
    for (int i = 2; i <= numParams; i++) {
      targetClass.getTypeParameterList().add(factory.createTypeParameterFromText("T" + (i-1), null));
    }
  }

  private void setupInheritance(PsiNewExpression element, PsiClass targetClass) throws IncorrectOperationException {
    if ((element.getParent() instanceof PsiReferenceExpression)) return;

    ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getInstance(getNewExpression().getProject()).getExpectedTypes(element, false);

    for (ExpectedTypeInfo expectedType : expectedTypes) {
      PsiType type = expectedType.getType();
      if (!(type instanceof PsiClassType)) continue;
      final PsiClassType classType = (PsiClassType)type;
      PsiClass aClass = classType.resolve();
      if (aClass == null) continue;
      if (aClass.equals(targetClass) || aClass.hasModifierProperty(PsiModifier.FINAL)) continue;
      PsiElementFactory factory = aClass.getManager().getElementFactory();

      if (aClass.isInterface()) {
        PsiReferenceList implementsList = targetClass.getImplementsList();
        implementsList.add(factory.createReferenceElementByType(classType));
      }
      else {
        PsiReferenceList extendsList = targetClass.getExtendsList();
        if (extendsList.getReferencedTypes().length > 0) continue;
        extendsList.add(factory.createReferenceElementByType(classType));
      }
    }
  }


  private PsiFile getTargetFile(PsiElement element) {
    PsiJavaCodeReferenceElement referenceElement = getReferenceElement((PsiNewExpression)element);

    if (referenceElement.getQualifier() instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement qualifier = (PsiJavaCodeReferenceElement)referenceElement.getQualifier();
      PsiElement psiElement = qualifier.resolve();
      if (psiElement instanceof PsiClass) {
        PsiClass psiClass = (PsiClass)psiElement;
        return psiClass.getContainingFile();
      }
    }

    return null;
  }

  protected PsiElement getElement() {
    final PsiNewExpression expression = getNewExpression();
    if (expression == null || !expression.getManager().isInProject(expression)) return null;
    PsiJavaCodeReferenceElement referenceElement = getReferenceElement(expression);
    if (referenceElement == null) return null;
    if (referenceElement.getReferenceNameElement() instanceof PsiIdentifier) return expression;

    return null;
  }

  protected boolean isAllowOuterTargetClass() {
    return false;
  }

  protected boolean isValidElement(PsiElement element) {
    PsiJavaCodeReferenceElement ref = PsiTreeUtil.getChildOfType(element, PsiJavaCodeReferenceElement.class);
    if (ref == null) return false;

    return ref.resolve() != null;
  }

  protected boolean isAvailableImpl(int offset) {
    PsiElement nameElement = getNameElement(getNewExpression());

    PsiFile targetFile = getTargetFile(getNewExpression());
    if (targetFile != null && !targetFile.getManager().isInProject(targetFile)) {
      return false;
    }

    if (shouldShowTag(offset, nameElement, getNewExpression())) {
      setText(QuickFixBundle.message("create.class.from.new.text", nameElement.getText()));
      return true;
    }

    return false;
  }

  private PsiJavaCodeReferenceElement getReferenceElement(PsiNewExpression expression) {
    return expression.getClassReference();
  }

  private PsiElement getNameElement(PsiNewExpression targetElement) {
    PsiJavaCodeReferenceElement referenceElement = getReferenceElement(targetElement);
    if (referenceElement == null) return null;
    return referenceElement.getReferenceNameElement();
  }

  public String getFamilyName() {
    return QuickFixBundle.message("create.class.from.new.family");
  }
}
