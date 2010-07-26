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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author ven
 */
public class CreatePropertyFromUsageFix extends CreateFromUsageBaseFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreatePropertyFromUsageFix");
  @NonNls private static final String FIELD_VARIABLE = "FIELD_NAME_VARIABLE";
  @NonNls private static final String TYPE_VARIABLE = "FIELD_TYPE_VARIABLE";
  @NonNls private static final String GET_PREFIX = "get";
  @NonNls private static final String IS_PREFIX = "is";
  @NonNls private static final String SET_PREFIX = "set";

  public CreatePropertyFromUsageFix(PsiMethodCallExpression methodCall) {
    myMethodCall = methodCall;
  }

  private final PsiMethodCallExpression myMethodCall;

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.property.from.usage.family");
  }

  protected PsiElement getElement() {
    if (!myMethodCall.isValid() || !myMethodCall.getManager().isInProject(myMethodCall)) return null;
    return myMethodCall;
  }

  protected boolean isAvailableImpl(int offset) {
    if (CreateMethodFromUsageFix.hasErrorsInArgumentList(myMethodCall)) return false;
    PsiReferenceExpression ref = myMethodCall.getMethodExpression();
    String methodName = myMethodCall.getMethodExpression().getReferenceName();
    LOG.assertTrue(methodName != null);
    String propertyName = PropertyUtil.getPropertyName(methodName);
    if (propertyName == null || propertyName.length() == 0) return false;

    String getterOrSetter = null;
    if (methodName.startsWith(GET_PREFIX) || methodName.startsWith(IS_PREFIX)) {
      if (myMethodCall.getArgumentList().getExpressions().length != 0) return false;
      getterOrSetter = QuickFixBundle.message("create.getter");
    }
    else if (methodName.startsWith(SET_PREFIX)) {
      if (myMethodCall.getArgumentList().getExpressions().length != 1) return false;
      getterOrSetter = QuickFixBundle.message("create.setter");
    }
    else {
      LOG.error("Internal error in create property intention");
    }

    List<PsiClass> classes = getTargetClasses(myMethodCall);
    if (classes.isEmpty()) return false;

    for (PsiClass aClass : classes) {
      if (!aClass.isInterface()) {
        if (CreateFromUsageUtils.shouldShowTag(offset, ref.getReferenceNameElement(), myMethodCall)) {
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

  static class FieldExpression extends Expression {
    private final String myDefaultFieldName;
    private final PsiField myField;
    private final PsiClass myClass;
    private final PsiType[] myExpectedTypes;

    public FieldExpression(PsiField field, PsiClass aClass, PsiType[] expectedTypes) {
      myField = field;
      myClass = aClass;
      myExpectedTypes = expectedTypes;
      myDefaultFieldName = field.getName();
    }

    public Result calculateResult(ExpressionContext context) {
      return new TextResult(myDefaultFieldName);
    }

    public Result calculateQuickResult(ExpressionContext context) {
      return new TextResult(myDefaultFieldName);
    }

    public LookupElement[] calculateLookupItems(ExpressionContext context) {
      Set<LookupElement> set = new LinkedHashSet<LookupElement>();
      set.add(LookupItemUtil.objectToLookupItem(myField));
      PsiField[] fields = myClass.getFields();
      for (PsiField otherField : fields) {
        if (!myDefaultFieldName.equals(otherField.getName())) {
          PsiType otherType = otherField.getType();
          for (PsiType type : myExpectedTypes) {
            if (type.equals(otherType)) {
              set.add(LookupItemUtil.objectToLookupItem(otherField));
            }
          }
        }
      }
      
      if (set.size() < 2) return null;
      return set.toArray(new LookupElement[set.size()]);
    }
  }

  @NotNull
  protected List<PsiClass> getTargetClasses(PsiElement element) {
    List<PsiClass> all = super.getTargetClasses(element);
    if (all.isEmpty()) return all;

    List<PsiClass> nonInterfaces = new ArrayList<PsiClass>();
    for (PsiClass aClass : all) {
      if (!aClass.isInterface()) nonInterfaces.add(aClass);
    }
    return nonInterfaces;
  }

  protected void invokeImpl(PsiClass targetClass) {
    PsiManager manager = myMethodCall.getManager();
    final Project project = manager.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

    boolean isStatic = false;
    PsiExpression qualifierExpression = myMethodCall.getMethodExpression().getQualifierExpression();
    if (qualifierExpression != null) {
      PsiReference reference = qualifierExpression.getReference();
      if (reference != null) {
        isStatic = reference.resolve() instanceof PsiClass;
      }
    }
    else {
      PsiMethod method = PsiTreeUtil.getParentOfType(myMethodCall, PsiMethod.class);
      if (method != null) {
        isStatic = method.hasModifierProperty(PsiModifier.STATIC);
      }
    }
    String fieldName = getVariableName(myMethodCall, isStatic);
    LOG.assertTrue(fieldName != null);
    String callText = myMethodCall.getMethodExpression().getReferenceName();
    LOG.assertTrue(callText != null, myMethodCall.getMethodExpression());
    PsiType[] expectedTypes;
    PsiType type;
    PsiField field = targetClass.findFieldByName(fieldName, true);
    if (callText.startsWith(GET_PREFIX)) {
      expectedTypes = field != null ? new PsiType[]{field.getType()} : CreateFromUsageUtils.guessType(myMethodCall, false);
      type = expectedTypes[0];
    }
    else if (callText.startsWith(IS_PREFIX)) {
      type = PsiType.BOOLEAN;
      expectedTypes = new PsiType[]{type};
    }
    else {
      type = myMethodCall.getArgumentList().getExpressions()[0].getType();
      if (type == null || PsiType.NULL.equals(type)) type = PsiType.getJavaLangObject(manager, myMethodCall.getResolveScope());
      expectedTypes = new PsiType[]{type};
    }

    positionCursor(project, targetClass.getContainingFile(), targetClass);

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

    try {

      if (field == null) {
        field = factory.createField(fieldName, type);
        PsiUtil.setModifierProperty(field, PsiModifier.STATIC, isStatic);
      }
      PsiMethod accessor;
      PsiElement fieldReference;
      PsiElement typeReference;
      PsiCodeBlock body;
      if (callText.startsWith(GET_PREFIX) || callText.startsWith(IS_PREFIX)) {
        accessor = (PsiMethod)targetClass.add(PropertyUtil.generateGetterPrototype(field));
        body = accessor.getBody();
        LOG.assertTrue(body != null, accessor.getText());
        fieldReference = ((PsiReturnStatement)body.getStatements()[0]).getReturnValue();
        typeReference = accessor.getReturnTypeElement();
      }
      else {
        accessor = (PsiMethod)targetClass.add(PropertyUtil.generateSetterPrototype(field, targetClass));
        body = accessor.getBody();
        LOG.assertTrue(body != null, accessor.getText());
        PsiAssignmentExpression expr = (PsiAssignmentExpression)((PsiExpressionStatement)body.getStatements()[0]).getExpression();
        fieldReference = ((PsiReferenceExpression)expr.getLExpression()).getReferenceNameElement();
        typeReference = accessor.getParameterList().getParameters()[0].getTypeElement();
      }
      accessor.setName(callText);
      PsiUtil.setModifierProperty(accessor, PsiModifier.STATIC, isStatic);

      TemplateBuilderImpl builder = new TemplateBuilderImpl(accessor);
      builder.replaceElement(typeReference, TYPE_VARIABLE, new TypeExpression(project, expectedTypes), true);
      builder.replaceElement(fieldReference, FIELD_VARIABLE, new FieldExpression(field, targetClass, expectedTypes), true);
      builder.setEndVariableAfter(body.getLBrace());

      accessor = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(accessor);
      targetClass = accessor.getContainingClass();
      LOG.assertTrue(targetClass != null);
      Template template = builder.buildTemplate();
      TextRange textRange = accessor.getTextRange();
      final PsiFile file = targetClass.getContainingFile();
      final Editor editor = positionCursor(project, targetClass.getContainingFile(), accessor);
      editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
      editor.getCaretModel().moveToOffset(textRange.getStartOffset());

      final boolean isStatic1 = isStatic;
      startTemplate(editor, template, project, new TemplateEditingAdapter() {
        public void beforeTemplateFinished(final TemplateState state, Template template) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              String fieldName = state.getVariableValue(FIELD_VARIABLE).getText();
              if (!JavaPsiFacade.getInstance(project).getNameHelper().isIdentifier(fieldName)) return;
              String fieldType = state.getVariableValue(TYPE_VARIABLE).getText();

              PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
              PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
              if (aClass == null) return;
              if (aClass.findFieldByName(fieldName, true) != null) return;
              PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
              try {
                PsiType type = factory.createTypeFromText(fieldType, aClass);
                try {
                  PsiField field = factory.createField(fieldName, type);
                  field = (PsiField)aClass.add(field);
                  PsiUtil.setModifierProperty(field, PsiModifier.STATIC, isStatic1);
                  positionCursor(project, field.getContainingFile(), field);
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
              }
              catch (IncorrectOperationException e) {
              }
            }
          });
        }
      });
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static String getVariableName(PsiMethodCallExpression methodCall, boolean isStatic) {
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(methodCall.getProject());
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
