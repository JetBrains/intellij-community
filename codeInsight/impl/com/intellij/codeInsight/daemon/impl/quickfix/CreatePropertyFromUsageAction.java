package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * @author ven
 */
public class CreatePropertyFromUsageAction extends CreateFromUsageBaseAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreatePropertyFromUsageAction");
  private static final @NonNls String FIELD_VARIABLE = "FIELD_NAME_VARIABLE";
  private static final @NonNls String TYPE_VARIABLE = "FIELD_TYPE_VARIABLE";
  private static final @NonNls String GET_PREFIX = "get";
  private static final @NonNls String IS_PREFIX = "is";
  private static final @NonNls String SET_PREFIX = "set";

  public CreatePropertyFromUsageAction(PsiMethodCallExpression methodCall) {
    myMethodCall = methodCall;
  }

  private final PsiMethodCallExpression myMethodCall;

  public String getFamilyName() {
    return QuickFixBundle.message("create.property.from.usage.family");
  }

  protected PsiElement getElement() {
    if (!myMethodCall.isValid() || !myMethodCall.getManager().isInProject(myMethodCall)) return null;
    return myMethodCall;
  }

  protected boolean isAvailableImpl(int offset) {
    PsiReferenceExpression ref = myMethodCall.getMethodExpression();
    String methodName = myMethodCall.getMethodExpression().getReferenceName();
    String propertyName = PropertyUtil.getPropertyName(methodName);
    if (propertyName == null || propertyName.length() == 0) return false;

    String getterOrSetter = null;
    if (methodName.startsWith(GET_PREFIX) || methodName.startsWith(IS_PREFIX)) {
      if (myMethodCall.getArgumentList().getExpressions().length != 0) return false;
      getterOrSetter = QuickFixBundle.message("create.getter");
    } else if (methodName.startsWith(SET_PREFIX)) {
      if (myMethodCall.getArgumentList().getExpressions().length != 1) return false;
      getterOrSetter = QuickFixBundle.message("create.setter");
    } else {
      LOG.error("Internal error in create property intention");
    }

    PsiClass[] classes = getTargetClasses(myMethodCall);
    if (classes == null) return false;

    for (PsiClass aClass : classes) {
      if (!aClass.isInterface()) {
        if (shouldShowTag(offset, ref.getReferenceNameElement(), myMethodCall)) {
          setText(getterOrSetter);
          return true;
        }
        else {
          return false;
        }
      }
    }

    return false;
  }

  static class FieldExpression implements Expression {
    private LookupItem[] myItems;
    private String myDefaultFieldName;

    public FieldExpression(PsiField field, PsiClass aClass, PsiType[] expectedTypes) {
      String fieldName = field.getName();
      Set<LookupItem> set = new LinkedHashSet<LookupItem>();
      LookupItemUtil.addLookupItem(set, field, "");
      PsiField[] fields = aClass.getFields();
      for (PsiField otherField : fields) {
        if (!fieldName.equals(otherField.getName())) {
          PsiType otherType = otherField.getType();
          for (PsiType type : expectedTypes) {
            if (type.equals(otherType)) {
              LookupItemUtil.addLookupItem(set, otherField, "");
            }
          }
        }
      }
      myDefaultFieldName = fieldName;
      myItems = set.toArray(new LookupItem[set.size()]);
    }

    public Result calculateResult(ExpressionContext context) {
      return new TextResult(myDefaultFieldName);
    }

    public Result calculateQuickResult(ExpressionContext context) {
      return new TextResult(myDefaultFieldName);
    }

    public LookupItem[] calculateLookupItems(ExpressionContext context) {
      if (myItems.length < 2) return null;
      return myItems;
    }
  }

  protected PsiClass[] getTargetClasses(PsiElement element) {
    PsiClass[] all = super.getTargetClasses(element);
    if (all == null) return null;

    List<PsiClass> nonInterfaces = new ArrayList<PsiClass>();
    for (PsiClass aClass : all) {
      if (!aClass.isInterface()) nonInterfaces.add(aClass);
    }
    return nonInterfaces.toArray(new PsiClass[nonInterfaces.size()]);
  }

  protected void invokeImpl(PsiClass targetClass) {

    PsiManager manager = myMethodCall.getManager();
    final Project project = manager.getProject();
    PsiElementFactory factory = manager.getElementFactory();

    boolean isStatic = false;
    PsiExpression qualifierExpression = myMethodCall.getMethodExpression().getQualifierExpression();
    if (qualifierExpression != null) {
      if (qualifierExpression.getReference() != null) {
        isStatic = qualifierExpression.getReference().resolve() instanceof PsiClass;
      }
    } else {
      PsiMethod method = PsiTreeUtil.getParentOfType(myMethodCall, PsiMethod.class);
      if (method != null) {
        isStatic = method.hasModifierProperty(PsiModifier.STATIC);
      }
    }
    String fieldName = getVariableName(myMethodCall, isStatic);
    LOG.assertTrue(fieldName != null);
    String callText = myMethodCall.getMethodExpression().getReferenceName();
    PsiType[] expectedTypes;
    PsiType type;
    if (callText.startsWith(GET_PREFIX)) {
      expectedTypes = CreateFromUsageUtils.guessType(myMethodCall, false);
      type = expectedTypes[0];
    } else if (callText.startsWith(IS_PREFIX)) {
      type = PsiType.BOOLEAN;
      expectedTypes = new PsiType[] {type};
    } else {
      type = myMethodCall.getArgumentList().getExpressions()[0].getType();
      if (type == null || type == PsiType.NULL) type = PsiType.getJavaLangObject(manager, myMethodCall.getResolveScope());
      expectedTypes = new PsiType[] {type};
    }

    positionCursor(project, targetClass.getContainingFile(), targetClass);

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

    try {
      PsiField field = targetClass.findFieldByName(fieldName, true);
      if (field == null) {
        field = factory.createField(fieldName, type);
        field.getModifierList().setModifierProperty(PsiModifier.STATIC, isStatic);
      }
      PsiMethod accessor;
      PsiElement fieldReference;
      PsiElement typeReference;
      if (callText.startsWith(GET_PREFIX) || callText.startsWith(IS_PREFIX)) {
        accessor = (PsiMethod) targetClass.add(PropertyUtil.generateGetterPrototype(field));
        fieldReference = ((PsiReturnStatement) accessor.getBody().getStatements()[0]).getReturnValue();
        typeReference = accessor.getReturnTypeElement();
      } else {
        accessor = (PsiMethod) targetClass.add(PropertyUtil.generateSetterPrototype(field));
        PsiAssignmentExpression expr = (PsiAssignmentExpression) ((PsiExpressionStatement) accessor.getBody().getStatements()[0]).getExpression();
        fieldReference = ((PsiReferenceExpression) expr.getLExpression()).getReferenceNameElement();
        typeReference = accessor.getParameterList().getParameters()[0].getTypeElement();
      }
      accessor.setName(callText);
      accessor.getModifierList().setModifierProperty(PsiModifier.STATIC, isStatic);

      TemplateBuilder builder = new TemplateBuilder(accessor);
      builder.replaceElement(typeReference, TYPE_VARIABLE, new TypeExpression(project, expectedTypes), true);
      builder.replaceElement(fieldReference, FIELD_VARIABLE, new FieldExpression(field, targetClass, expectedTypes), true);
      builder.setEndVariableAfter(accessor.getBody().getLBrace());

      accessor = CodeInsightUtil.forcePsiPostprocessAndRestoreElement(accessor);
      targetClass = accessor.getContainingClass();
      LOG.assertTrue (targetClass != null);
      Template template = builder.buildTemplate();
      TextRange textRange = accessor.getTextRange();
      final PsiFile file = targetClass.getContainingFile();
      final Editor editor = positionCursor(project, targetClass.getContainingFile(), accessor);
      editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
      editor.getCaretModel().moveToOffset(textRange.getStartOffset());

      final boolean isStatic1 = isStatic;
      startTemplate(editor, template, project, new TemplateEditingAdapter() {
        public void templateFinished(Template template) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              TemplateState state = TemplateManagerImpl.getTemplateState(editor);
              if (state == null) return;
              String fieldName = state.getVariableValue(FIELD_VARIABLE).getText();
              if (!PsiManager.getInstance(project).getNameHelper().isIdentifier(fieldName)) return;
              String fieldType = state.getVariableValue(TYPE_VARIABLE).getText();

              PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
              PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
              if (aClass == null) return;
              if (aClass.findFieldByName(fieldName, true) != null) return;
              PsiElementFactory factory = aClass.getManager().getElementFactory();
              try {
                PsiType type = factory.createTypeFromText(fieldType, aClass);
                try {
                  PsiField field = factory.createField(fieldName, type);
                  field = (PsiField) aClass.add(field);
                  field.getModifierList().setModifierProperty(PsiModifier.STATIC, isStatic1);
                  positionCursor(project, field.getContainingFile(), field);
                } catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
              } catch (IncorrectOperationException e) {
                return;
              }
            }
          });
        }
      });

    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static String getVariableName(PsiMethodCallExpression methodCall, boolean isStatic) {
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(methodCall.getProject());
    String methodName = methodCall.getMethodExpression().getReferenceName();
    String propertyName = PropertyUtil.getPropertyName(methodName);
    if (propertyName != null && propertyName.length() > 0) {
        VariableKind kind = isStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;
        return codeStyleManager.propertyNameToVariableName(propertyName, kind);
    }

    return null;
  }

  protected boolean isValidElement(PsiElement element) {
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression) element;
    return methodCall.getMethodExpression().resolve() != null;
  }
}
