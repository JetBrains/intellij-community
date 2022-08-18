// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RefFunctionalExpressionImpl extends RefJavaElementImpl implements RefFunctionalExpression {

  RefFunctionalExpressionImpl(@NotNull UExpression expr, @NotNull PsiElement psi, @NotNull RefManager manager) {
    super(expr, psi, manager);
  }

  @Override
  protected synchronized void initialize() {
    UExpression element = getUastElement();
    LOG.assertTrue(element != null);
    PsiElement sourceElement = element.getSourcePsi();
    LOG.assertTrue(sourceElement != null);
    setOwner();
    if (element instanceof ULambdaExpression) {
      setParameters(((ULambdaExpression)element).getParameters());
    }
    else if (element instanceof UCallableReferenceExpression) {
      PsiMethod resolvedMethod = LambdaUtil.getFunctionalInterfaceMethod(sourceElement);
      UMethod uMethodRef = UastContextKt.toUElement(resolvedMethod, UMethod.class);
      if (uMethodRef != null) {
        setParameters(uMethodRef.getUastParameters());
      }
    }
    else {
      assert false;
    }
    setHasEmptyBody();
  }

  @Override
  public void buildReferences() {
    UExpression element = getUastElement();
    LOG.assertTrue(element != null);
    PsiElement sourceElement = element.getSourcePsi();
    LOG.assertTrue(sourceElement != null);
    final PsiMethod resolvedMethod = LambdaUtil.getFunctionalInterfaceMethod(sourceElement);
    if (resolvedMethod != null) {
      RefMethod resolvedRefMethod = ObjectUtils.tryCast(getRefManager().getReference(resolvedMethod), RefMethod.class);
      if (resolvedRefMethod != null) {
        resolvedRefMethod.addDerivedReference(this);
        resolvedRefMethod.initializeIfNeeded();
        RefClass refClass = resolvedRefMethod.getOwnerClass();
        if (refClass != null) {
          refClass.addDerivedReference(this);
        }
        if (element instanceof UCallableReferenceExpression && !TypeConversionUtil.isVoidType(resolvedMethod.getReturnType())) {
          ((RefMethodImpl)resolvedRefMethod).updateReturnValueTemplate(element);
        }
      }
    }
    if (element instanceof ULambdaExpression) {
      RefJavaUtil.getInstance().addReferencesTo(element, this, ((ULambdaExpression)element).getBody());
    }
    else if (element instanceof UCallableReferenceExpression) {
      RefJavaUtil.getInstance().addReferencesTo(element, this, element);
      for (RefParameter parameter : getParameters()) {
        addReference(parameter, parameter.getPsiElement(), element, false, true, null);
      }
    }
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
    LOG.assertTrue(isInitialized());
    return ContainerUtil.filterIsInstance(getChildren(), RefParameter.class);
  }

  @Nullable
  @Override
  public UExpression getUastElement() {
    return UastContextKt.toUElement(getPsiElement(), UExpression.class);
  }

  @Override
  public synchronized boolean hasEmptyBody() {
    LOG.assertTrue(isInitialized());
    return checkFlag(RefMethodImpl.IS_BODY_EMPTY_MASK);
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
    UElement pDeclaration = UastUtils.getParentOfType(element, true, UMethod.class, UClass.class, ULambdaExpression.class, UField.class);
    if (pDeclaration != null) {
      RefElement pDeclarationRef = getRefManager().getReference(pDeclaration.getSourcePsi());
      if (pDeclarationRef != null) {
        ((WritableRefEntity)pDeclarationRef).add(this);
      }
    }
  }

  private void setParameters(@NotNull List<UParameter> parameters) {
    if (parameters.isEmpty()) return;
    UExpression element = getUastElement();
    assert element != null;
    for (int i = 0; i < parameters.size(); i++) {
      UParameter param = parameters.get(i);
      if (param.getSourcePsi() != null) {
        getRefJavaManager().getParameterReference(param, i, this);
      }
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
    setFlag(isEmptyBody, RefMethodImpl.IS_BODY_EMPTY_MASK);
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
