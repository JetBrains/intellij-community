/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class CreateMethodFromMethodReferenceFix extends CreateFromUsageBaseFix {
  private static final Logger LOG = Logger.getInstance(CreateMethodFromMethodReferenceFix.class);

  private final SmartPsiElementPointer myMethodReferenceExpression;

  public CreateMethodFromMethodReferenceFix(@NotNull PsiMethodReferenceExpression methodRef) {
    myMethodReferenceExpression = SmartPointerManager.getInstance(methodRef.getProject()).createSmartPsiElementPointer(methodRef);
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    final PsiMethodReferenceExpression call = getMethodReference();
    if (call == null || !call.isValid()) return false;
    final PsiType functionalInterfaceType = getFunctionalExpressionType(call);
    if (functionalInterfaceType == null || 
        LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType) == null){
      return false;
    }

    final String name = call.getReferenceName();

    if (name == null) return false;
    if (call.isConstructor() && name.equals(PsiKeyword.NEW) || PsiNameHelper.getInstance(call.getProject()).isIdentifier(name)) {
      setText(call.isConstructor() ? QuickFixBundle.message("create.constructor.from.new.text") : QuickFixBundle.message("create.method.from.usage.text", name));
      return true;
    }
    return false;
  }

  private static PsiType getFunctionalExpressionType(PsiMethodReferenceExpression ref) {
    PsiType functionalInterfaceType = ref.getFunctionalInterfaceType();
    if (functionalInterfaceType != null) return functionalInterfaceType;
    Ref<PsiType> type = new Ref<>();
    if (LambdaUtil.processParentOverloads(ref, (fType) -> type.set(fType))) {
      return type.get();
    }
    return null;
  }

  @Override
  protected PsiElement getElement() {
    final PsiMethodReferenceExpression call = getMethodReference();
    if (call == null || !canModify(call)) return null;
    return call;
  }

  @Override
  @NotNull
  protected List<PsiClass> getTargetClasses(PsiElement element) {
    List<PsiClass> targets = super.getTargetClasses(element);
    PsiMethodReferenceExpression call = getMethodReference();
    if (call == null) return Collections.emptyList();
    return targets;
  }

  @Override
  protected boolean canBeTargetClass(PsiClass psiClass) {
    PsiMethodReferenceExpression reference = getMethodReference();
    if (reference != null && reference.isConstructor()) {
      return false;
    }
    return super.canBeTargetClass(psiClass);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    chooseTargetClass(project, editor, this::invokeImpl);
  }

  private void invokeImpl(@NotNull PsiClass targetClass) {
    PsiMethodReferenceExpression expression = getMethodReference();
    if (expression == null) return;

    if (isValidElement(expression)) return;

    PsiClass parentClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
    PsiMember enclosingContext = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, PsiField.class, PsiClassInitializer.class);

    String methodName = expression.getReferenceName();
    LOG.assertTrue(methodName != null);


    final Project project = targetClass.getProject();
    JVMElementFactory elementFactory = JVMElementFactories.getFactory(targetClass.getLanguage(), project);
    if (elementFactory == null) elementFactory = JavaPsiFacade.getElementFactory(project);

    PsiMethod method = expression.isConstructor() ? (PsiMethod)targetClass.add(elementFactory.createConstructor()) 
                                                  : CreateMethodFromUsageFix.createMethod(targetClass, parentClass, enclosingContext, methodName);
    if (method == null) {
      return;
    }

    if (!expression.isConstructor()) {
      setupVisibility(parentClass, targetClass, method.getModifierList());
    }

    expression = getMethodReference();
    LOG.assertTrue(expression.isValid());

    boolean shouldBeAbstract = false;
    if (!expression.isConstructor()) {
      if (shouldCreateStaticMember(expression, targetClass)) {
        PsiUtil.setModifierProperty(method, PsiModifier.STATIC, true);
      }
      else if (targetClass.isInterface()) {
        shouldBeAbstract = true;
        PsiCodeBlock body = method.getBody();
        assert body != null;
        body.delete();
      }
    }

    final PsiElement context = PsiTreeUtil.getParentOfType(expression, PsiClass.class, PsiMethod.class);

    final PsiType functionalInterfaceType = getFunctionalExpressionType(expression);
    final PsiClassType.ClassResolveResult classResolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(classResolveResult);
    LOG.assertTrue(interfaceMethod != null);

    final PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);
    LOG.assertTrue(interfaceReturnType != null);

    final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(interfaceMethod, classResolveResult);
    final ExpectedTypeInfo[] expectedTypes = {new ExpectedTypeInfoImpl(interfaceReturnType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, interfaceReturnType, TailType.NONE, null, ExpectedTypeInfoImpl.NULL)};
    CreateMethodFromUsageFix.doCreate(targetClass, method, shouldBeAbstract,
                                      ContainerUtil.map2List(interfaceMethod.getParameterList().getParameters(), parameter -> Pair.create(null, substitutor.substitute(parameter.getType()))),
                                      PsiSubstitutor.EMPTY,
                                      expectedTypes, context);
  }

  

  @Override
  protected boolean isValidElement(PsiElement element) {
    return false;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.method.from.usage.family");
  }

  @Nullable
  protected PsiMethodReferenceExpression getMethodReference() {
    return (PsiMethodReferenceExpression)myMethodReferenceExpression.getElement();
  }
}
