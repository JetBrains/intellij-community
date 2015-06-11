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
package com.intellij.refactoring.encapsulateFields;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Max Medvedev
 */
public class JavaEncapsulateFieldHelper extends EncapsulateFieldHelper {
  private static final Logger LOG = Logger.getInstance(JavaEncapsulateFieldHelper.class);

  @Nullable
  public EncapsulateFieldUsageInfo createUsage(@NotNull EncapsulateFieldsDescriptor descriptor,
                                               @NotNull FieldDescriptor fieldDescriptor,
                                               @NotNull PsiReference reference) {
    if (!(reference instanceof PsiReferenceExpression)) return null;

    boolean findSet = descriptor.isToEncapsulateSet();
    boolean findGet = descriptor.isToEncapsulateGet();
    PsiReferenceExpression ref = (PsiReferenceExpression)reference;
    // [Jeka] to avoid recursion in the field's accessors
    if (findGet && isUsedInExistingAccessor(descriptor.getTargetClass(), fieldDescriptor.getGetterPrototype(), ref)) return null;
    if (findSet && isUsedInExistingAccessor(descriptor.getTargetClass(), fieldDescriptor.getSetterPrototype(), ref)) return null;
    if (!findGet) {
      if (!PsiUtil.isAccessedForWriting(ref)) return null;
    }
    if (!findSet || fieldDescriptor.getField().hasModifierProperty(PsiModifier.FINAL)) {
      if (!PsiUtil.isAccessedForReading(ref)) return null;
    }
    if (!descriptor.isToUseAccessorsWhenAccessible()) {
      PsiModifierList newModifierList = createNewModifierList(descriptor);

      PsiClass accessObjectClass = null;
      PsiExpression qualifier = ref.getQualifierExpression();
      if (qualifier != null) {
        accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
      }
      final PsiResolveHelper helper = JavaPsiFacade.getInstance(((PsiReferenceExpression)reference).getProject()).getResolveHelper();
      if (helper.isAccessible(fieldDescriptor.getField(), newModifierList, ref, accessObjectClass, null)) {
        return null;
      }
    }
    return new EncapsulateFieldUsageInfo(ref, fieldDescriptor);
  }

  public static PsiModifierList createNewModifierList(EncapsulateFieldsDescriptor descriptor) {
    PsiModifierList newModifierList = null;
    PsiElementFactory factory = JavaPsiFacade.getInstance(descriptor.getTargetClass().getProject()).getElementFactory();
    try {
      PsiField field = factory.createField("a", PsiType.INT);
      EncapsulateFieldsProcessor.setNewFieldVisibility(field, descriptor);
      newModifierList = field.getModifierList();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return newModifierList;
  }

  public static boolean isUsedInExistingAccessor(PsiClass aClass, PsiMethod prototype, PsiElement element) {
    PsiMethod existingAccessor = aClass.findMethodBySignature(prototype, false);
    if (existingAccessor != null) {
      PsiElement parent = element;
      while (parent != null) {
        if (existingAccessor.equals(parent)) return true;
        parent = parent.getParent();
      }
    }
    return false;
  }

  public boolean processUsage(@NotNull EncapsulateFieldUsageInfo usage,
                              @NotNull EncapsulateFieldsDescriptor descriptor,
                              PsiMethod setter,
                              PsiMethod getter) {
    final PsiElement element = usage.getElement();
    if (!(element instanceof PsiReferenceExpression)) return false;

    final FieldDescriptor fieldDescriptor = usage.getFieldDescriptor();
    PsiField field = fieldDescriptor.getField();
    boolean processGet = descriptor.isToEncapsulateGet();
    boolean processSet = descriptor.isToEncapsulateSet() && !field.hasModifierProperty(PsiModifier.FINAL);
    if (!processGet && !processSet) return true;
    PsiElementFactory factory = JavaPsiFacade.getInstance(descriptor.getTargetClass().getProject()).getElementFactory();

    try{
      final PsiReferenceExpression expr = (PsiReferenceExpression)element;
      final PsiElement parent = expr.getParent();
      if (parent instanceof PsiAssignmentExpression && expr.equals(((PsiAssignmentExpression)parent).getLExpression())){
        PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
        if (assignment.getRExpression() == null) return true;
        PsiJavaToken opSign = assignment.getOperationSign();
        IElementType opType = opSign.getTokenType();
        if (opType == JavaTokenType.EQ) {
          {
            if (!processSet) return true;
            final PsiExpression setterArgument = assignment.getRExpression();

            PsiMethodCallExpression methodCall = createSetterCall(fieldDescriptor, setterArgument, expr, descriptor.getTargetClass(), setter);

            if (methodCall != null) {
              assignment.replace(methodCall);
            }
            //TODO: check if value is used!!!
          }
        }
        else if (opType == JavaTokenType.ASTERISKEQ || opType == JavaTokenType.DIVEQ || opType == JavaTokenType.PERCEQ ||
                 opType == JavaTokenType.PLUSEQ ||
                 opType == JavaTokenType.MINUSEQ ||
                 opType == JavaTokenType.LTLTEQ ||
                 opType == JavaTokenType.GTGTEQ ||
                 opType == JavaTokenType.GTGTGTEQ ||
                 opType == JavaTokenType.ANDEQ ||
                 opType == JavaTokenType.OREQ ||
                 opType == JavaTokenType.XOREQ) {
          {
            // Q: side effects of qualifier??!

            String opName = opSign.getText();
            LOG.assertTrue(StringUtil.endsWithChar(opName, '='));
            opName = opName.substring(0, opName.length() - 1);

            PsiExpression getExpr = expr;
            if (processGet) {
              final PsiMethodCallExpression getterCall = createGetterCall(fieldDescriptor, expr, descriptor.getTargetClass(), getter);
              if (getterCall != null) {
                getExpr = getterCall;
              }
            }

            @NonNls String text = "a" + opName + "b";
            PsiBinaryExpression binExpr = (PsiBinaryExpression)factory.createExpressionFromText(text, expr);
            binExpr.getLOperand().replace(getExpr);
            binExpr.getROperand().replace(assignment.getRExpression());

            PsiExpression setExpr;
            if (processSet) {
              setExpr = createSetterCall(fieldDescriptor, binExpr, expr, descriptor.getTargetClass(), setter);
            }
            else {
              text = "a = b";
              PsiAssignmentExpression assignment1 = (PsiAssignmentExpression)factory.createExpressionFromText(text, null);
              assignment1.getLExpression().replace(expr);
              assignment1.getRExpression().replace(binExpr);
              setExpr = assignment1;
            }

            assignment.replace(setExpr);
            //TODO: check if value is used!!!
          }
        }
      }
      else if (RefactoringUtil.isPlusPlusOrMinusMinus(parent)){
        IElementType sign;
        if (parent instanceof PsiPrefixExpression){
          sign = ((PsiPrefixExpression)parent).getOperationTokenType();
        }
        else{
          sign = ((PsiPostfixExpression)parent).getOperationTokenType();
        }

        PsiExpression getExpr = expr;
        if (processGet){
          final PsiMethodCallExpression getterCall = createGetterCall(fieldDescriptor, expr, descriptor.getTargetClass(), getter);
          if (getterCall != null) {
            getExpr = getterCall;
          }
        }

        @NonNls String text;
        if (sign == JavaTokenType.PLUSPLUS){
          text = "a+1";
        }
        else{
          text = "a-1";
        }
        PsiBinaryExpression binExpr = (PsiBinaryExpression)factory.createExpressionFromText(text, null);
        binExpr.getLOperand().replace(getExpr);

        PsiExpression setExpr;
        if (processSet){
          setExpr = createSetterCall(fieldDescriptor, binExpr, expr, descriptor.getTargetClass(), setter);
        }
        else {
          text = "a = b";
          PsiAssignmentExpression assignment = (PsiAssignmentExpression)factory.createExpressionFromText(text, null);
          assignment.getLExpression().replace(expr);
          assignment.getRExpression().replace(binExpr);
          setExpr = assignment;
        }
        parent.replace(setExpr);
      }
      else{
        if (!processGet) return true;
        PsiMethodCallExpression methodCall = createGetterCall(fieldDescriptor, expr, descriptor.getTargetClass(), getter);

        if (methodCall != null) {
          expr.replace(methodCall);
        }
      }
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
    return true;
  }

  private static PsiMethodCallExpression createSetterCall(FieldDescriptor fieldDescriptor,
                                                          PsiExpression setterArgument,
                                                          PsiReferenceExpression expr,
                                                          PsiClass aClass,
                                                          PsiMethod setter) throws IncorrectOperationException {
    final String setterName = fieldDescriptor.getSetterName();
    @NonNls String text = setterName + "(a)";
    PsiMethodCallExpression methodCall = prepareMethodCall(expr,  text);
    methodCall.getArgumentList().getExpressions()[0].replace(setterArgument);
    methodCall = checkMethodResolvable(methodCall, setter, expr, aClass);
    if (methodCall == null) {
      VisibilityUtil.escalateVisibility(fieldDescriptor.getField(), expr);
    }
    return methodCall;
  }

  @Nullable
  private static PsiMethodCallExpression createGetterCall(FieldDescriptor fieldDescriptor,
                                                          PsiReferenceExpression expr,
                                                          PsiClass aClass,
                                                          PsiMethod getter) throws IncorrectOperationException {
    final String getterName = fieldDescriptor.getGetterName();
    @NonNls String text = getterName + "()";
    PsiMethodCallExpression methodCall = prepareMethodCall(expr, text);
    methodCall = checkMethodResolvable(methodCall, getter, expr, aClass);
    if (methodCall == null) {
      VisibilityUtil.escalateVisibility(fieldDescriptor.getField(), expr);
    }
    return methodCall;
  }

  private static PsiMethodCallExpression prepareMethodCall(PsiReferenceExpression expr, String text) {
    PsiExpression qualifier = expr.getQualifierExpression();
    if (qualifier != null) {
      final PsiElement referenceNameElement = expr.getReferenceNameElement();
      if (referenceNameElement != null) {
        text = expr.getText().substring(0, referenceNameElement.getStartOffsetInParent()) + text;
      }
    }
    final PsiElementFactory factory = JavaPsiFacade.getInstance(expr.getProject()).getElementFactory();
    return (PsiMethodCallExpression)factory.createExpressionFromText(text, expr);
  }

  @Nullable
  private static PsiMethodCallExpression checkMethodResolvable(PsiMethodCallExpression methodCall,
                                                               PsiMethod targetMethod,
                                                               PsiReferenceExpression context,
                                                               PsiClass aClass) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(targetMethod.getProject()).getElementFactory();
    final PsiElement resolved = methodCall.getMethodExpression().resolve();
    if (resolved != targetMethod) {
      PsiClass containingClass;
      if (resolved instanceof PsiMethod) {
        containingClass = ((PsiMethod)resolved).getContainingClass();
      }
      else if (resolved instanceof PsiClass) {
        containingClass = (PsiClass)resolved;
      }
      else {
        return null;
      }
      if (containingClass != null && containingClass.isInheritor(aClass, false)) {
        final PsiExpression newMethodExpression =
          factory.createExpressionFromText("super." + targetMethod.getName(), context);
        methodCall.getMethodExpression().replace(newMethodExpression);
      }
      else {
        methodCall = null;
      }
    }
    return methodCall;
  }

  @NotNull
  @Override
  public PsiField[] getApplicableFields(@NotNull PsiClass aClass) {
    final List<PsiField> fields = ContainerUtil.filter(aClass.getFields(), new Condition<PsiField>() {
      @Override
      public boolean value(PsiField field) {
        return !(field instanceof PsiEnumConstant);
      }
    });
    return fields.toArray(new PsiField[fields.size()]);
  }

  @Override
  @NotNull
  public String suggestSetterName(@NotNull PsiField field) {
    return GenerateMembersUtil.suggestSetterName(field);
  }

  @Override
  @NotNull
  public String suggestGetterName(@NotNull PsiField field) {
    return GenerateMembersUtil.suggestGetterName(field);
  }

  @Override
  @Nullable
  public PsiMethod generateMethodPrototype(@NotNull PsiField field, @NotNull String methodName, boolean isGetter) {
    PsiMethod prototype = isGetter
                          ? GenerateMembersUtil.generateGetterPrototype(field)
                          : GenerateMembersUtil.generateSetterPrototype(field);
    try {
      prototype.setName(methodName);
      return prototype;
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }
}
