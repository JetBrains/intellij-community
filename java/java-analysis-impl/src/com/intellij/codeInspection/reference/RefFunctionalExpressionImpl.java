// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RefFunctionalExpressionImpl extends RefJavaElementImpl implements RefFunctionalExpression {
  private List<RefParameter> myParameters;
  private boolean hasEmptyBody;

  protected RefFunctionalExpressionImpl(@NotNull UExpression expr, @NotNull PsiElement psi, @NotNull RefManager manager) {
    super(expr, psi, manager);
  }

  @Override
  protected void initialize() {
    UExpression element = getUastElement();
    if (element == null) return;
    PsiElement sourceElement = element.getSourcePsi();
    if (sourceElement == null) return;
    setOwner();
    PsiMethod resolvedMethod = null;
    RefMethod resolvedRefMethod = null;
    List<UParameter> parameters = null;
    boolean isMethodReference = false;
    if (element instanceof ULambdaExpression) {
      resolvedMethod = LambdaUtil.getFunctionalInterfaceMethod(sourceElement);
      resolvedRefMethod = ObjectUtils.tryCast(getRefManager().getReference(resolvedMethod), RefMethod.class);
      parameters = ((ULambdaExpression)element).getParameters();
    }
    else if (element instanceof UCallableReferenceExpression) {
      isMethodReference = true;
      resolvedMethod = LambdaUtil.getFunctionalInterfaceMethod(sourceElement);
      resolvedRefMethod = ObjectUtils.tryCast(getRefManager().getReference(resolvedMethod), RefMethod.class);
      UMethod uMethodRef = UastContextKt.toUElement(resolvedMethod, UMethod.class);
      if (uMethodRef != null) {
        parameters = uMethodRef.getUastParameters();
      }
    }
    setParameters(parameters);
    if (resolvedRefMethod != null && resolvedMethod != null) {
      resolvedRefMethod.addDerivedReference(this);
      RefClass refClass = resolvedRefMethod.getOwnerClass();
      if (refClass != null) {
        refClass.addDerivedReference(this);
      }
      if (isMethodReference && !TypeConversionUtil.isVoidType(resolvedMethod.getReturnType())) {
        ((RefMethodImpl)resolvedRefMethod).updateReturnValueTemplate(element);
      }
    }
    setHasEmptyBody();
  }

  @Override
  public void buildReferences() {
    UExpression element = getUastElement();
    if (element == null) return;
    if (element instanceof ULambdaExpression) {
      RefJavaUtil.getInstance().addReferencesTo(element, this, ((ULambdaExpression)element).getBody());
    }
    else if (element instanceof UCallableReferenceExpression) {
      RefJavaUtil.getInstance().addReferencesTo(element, this, element);
      for (RefParameter parameter : getParameters()) {
        addReference(parameter, parameter.getPsiElement(), element, false, true, null);
      }
    }
    getRefManager().fireBuildReferences(this);
  }

  @Override
  @NotNull
  public Collection<? extends RefOverridable> getDerivedReferences() {
    return Collections.emptyList();
  }

  @Override
  public void addDerivedReference(@NotNull RefOverridable reference) {
    // do nothing
  }

  @NotNull
  @Override
  public synchronized List<RefParameter> getParameters() {
    return ObjectUtils.notNull(myParameters, Collections.emptyList());
  }

  @Nullable
  @Override
  public UExpression getUastElement() {
    return UastContextKt.toUElement(getPsiElement(), UExpression.class);
  }

  @Override
  public synchronized boolean hasEmptyBody() {
    return hasEmptyBody;
  }

  @Override
  public void accept(@NotNull RefVisitor visitor) {
    if (visitor instanceof RefJavaVisitor) {
      ApplicationManager.getApplication().runReadAction(() -> ((RefJavaVisitor)visitor).visitFunctionalExpression(this));
    }
    else {
      super.accept(visitor);
    }
  }

  private void setOwner() {
    UExpression element = getUastElement();
    assert element != null;
    UDeclaration elementDeclaration = UDeclarationKt.getContainingDeclaration(element);
    if (elementDeclaration == null) return;
    UElement pDeclaration = getParentDeclaration(element);
    if (pDeclaration != null) {
      RefElement pDeclarationRef = getRefManager().getReference(pDeclaration.getSourcePsi());
      if (pDeclarationRef != null) {
        ((WritableRefEntity)pDeclarationRef).add(this);
      }
    }
  }

  private void setParameters(@Nullable List<UParameter> parameters) {
    if (ContainerUtil.isEmpty(parameters)) return;
    UExpression element = getUastElement();
    assert element != null;
    List<RefParameter> refParameters = new ArrayList<>(parameters.size());
    for (int i = 0; i < parameters.size(); i++) {
      UParameter param = parameters.get(i);
      if (param.getSourcePsi() != null) {
        RefParameter refParameter = getRefJavaManager().getParameterReference(param, i, this);
        if (refParameter == null) continue;
        refParameters.add(refParameter);
      }
    }
    synchronized (this) {
      myParameters = refParameters;
    }
  }

  private void setHasEmptyBody() {
    UExpression element = getUastElement();
    assert element != null;
    boolean isEmptyBody = false;
    if (element instanceof ULambdaExpression) {
      PsiType type = ((ULambdaExpression)element).getFunctionalInterfaceType();
      UBlockExpression body = ObjectUtils.tryCast(((ULambdaExpression)element).getBody(), UBlockExpression.class);
      if (body != null && (body.getExpressions().isEmpty() || checkIfOnlyCallsSuper(body, type))) {
        isEmptyBody = true;
      }
    }
    synchronized (this) {
      hasEmptyBody = isEmptyBody;
    }
  }

  @Nullable
  private static UElement getParentDeclaration(@NotNull UExpression expr) {
    UDeclaration elementDeclaration = UDeclarationKt.getContainingDeclaration(expr);
    if (elementDeclaration == null) return null;
    return UastUtils.getParentOfType(elementDeclaration, false, UMethod.class, UClass.class, ULambdaExpression.class, UField.class);
  }

  private static boolean checkIfOnlyCallsSuper(@NotNull UBlockExpression body, @Nullable PsiType type) {
    List<UExpression> expressions = body.getExpressions();
    if (expressions.size() > 1) return false;
    UExpression expression = expressions.get(0);
    if (expression instanceof UReturnExpression) {
      expression = ((UReturnExpression)expression).getReturnExpression();
    }
    if (expression instanceof UQualifiedReferenceExpression) {
      UMethod lambdaMethod = UastContextKt.toUElement(LambdaUtil.getFunctionalInterfaceMethod(type), UMethod.class);
      return lambdaMethod != null && RefJavaUtil.getInstance().isCallToSuperMethod(expression, lambdaMethod);
    }
    return false;
  }
}
