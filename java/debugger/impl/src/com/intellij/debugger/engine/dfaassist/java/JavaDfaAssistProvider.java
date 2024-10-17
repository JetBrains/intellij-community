// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist.java;

import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.TypeConstraints;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.ArrayElementDescriptor;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.dfaassist.DebuggerDfaListener;
import com.intellij.debugger.engine.dfaassist.DfaAssistProvider;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.expression.CaptureTraverser;
import com.intellij.debugger.engine.jdi.LocalVariableProxy;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyEx;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaDfaAssistProvider implements DfaAssistProvider {
  @Override
  public boolean locationMatches(@NotNull PsiElement element, @NotNull Location location) {
    Method method = location.method();
    PsiElement context = DebuggerUtilsEx.getContainingMethod(element);
    if (context instanceof PsiMethod psiMethod) {
      String name = psiMethod.isConstructor() ? "<init>" : psiMethod.getName();
      return name.equals(method.name()) && psiMethod.getParameterList().getParametersCount() == method.argumentTypeNames().size();
    }
    if (context instanceof PsiLambdaExpression lambda) {
      return DebuggerUtilsEx.isLambda(method) &&
             method.argumentTypeNames().size() >= lambda.getParameterList().getParametersCount();
    }
    if (context instanceof PsiClassInitializer initializer) {
      String expectedMethod = initializer.hasModifierProperty(PsiModifier.STATIC) ? "<clinit>" : "<init>";
      return method.name().equals(expectedMethod);
    }
    return false;
  }

  @Override
  public @Nullable PsiElement getAnchor(@NotNull PsiElement element) {
    while (element instanceof PsiWhiteSpace || element instanceof PsiComment) {
      element = element.getNextSibling();
    }
    while (!(element instanceof PsiStatement)) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiStatement) && (parent == null || element.getTextRangeInParent().getStartOffset() > 0)) {
        if (parent instanceof PsiCodeBlock && ((PsiCodeBlock)parent).getRBrace() == element) {
          PsiElement grandParent = parent.getParent();
          if (grandParent instanceof PsiBlockStatement) {
            return PsiTreeUtil.getNextSiblingOfType(grandParent, PsiStatement.class);
          }
        }
        if (parent instanceof PsiPolyadicExpression) {
          // If we are inside the expression we can position only at locations where the stack is empty
          // currently only && and || chains inside if/return/yield are allowed
          IElementType tokenType = ((PsiPolyadicExpression)parent).getOperationTokenType();
          if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
            PsiElement grandParent = parent.getParent();
            if (grandParent instanceof PsiIfStatement || grandParent instanceof PsiYieldStatement ||
                grandParent instanceof PsiReturnStatement) {
              if (element instanceof PsiExpression) {
                return element;
              }
              return PsiTreeUtil.getNextSiblingOfType(element, PsiExpression.class);
            }
          }
        }
        return null;
      }
      element = parent;
    }
    return element;
  }

  @Override
  public @Nullable PsiElement getCodeBlock(@NotNull PsiElement anchor) {
    if (anchor instanceof PsiSwitchLabelStatementBase) {
      return null; // unsupported yet
    }
    PsiElement e = anchor;
    while (e != null && !(e instanceof PsiClass) && !(e instanceof PsiFileSystemItem)) {
      e = e.getParent();
      if (e instanceof PsiCodeBlock) {
        PsiElement parent = e.getParent();
        if (parent instanceof PsiMethod || parent instanceof PsiLambdaExpression || parent instanceof PsiClassInitializer ||
            // We cannot properly restore context if we started from finally, so let's analyze just finally block
            parent instanceof PsiTryStatement && ((PsiTryStatement)parent).getFinallyBlock() == e ||
            parent instanceof PsiBlockStatement &&
            (parent.getParent() instanceof PsiSwitchLabeledRuleStatement &&
             ((PsiSwitchLabeledRuleStatement)parent.getParent()).getEnclosingSwitchBlock() instanceof PsiSwitchExpression)) {
          return e;
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Value getJdiValueForDfaVariable(@NotNull StackFrameProxyEx proxy,
                                         @NotNull DfaVariableValue var,
                                         @NotNull PsiElement anchor) throws EvaluateException {
    if (var.getQualifier() != null) {
      VariableDescriptor descriptor = var.getDescriptor();
      if (descriptor instanceof SpecialField) {
        // Special fields facts are applied from qualifiers
        return null;
      }
      Value qualifierValue = getJdiValueForDfaVariable(proxy, var.getQualifier(), anchor);
      if (qualifierValue == null) return null;
      PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiField && qualifierValue instanceof ObjectReference) {
        ReferenceType type = ((ObjectReference)qualifierValue).referenceType();
        PsiClass psiClass = ((PsiField)element).getContainingClass();
        if (psiClass != null && type.name().equals(JVMNameUtil.getClassVMName(psiClass))) {
          Field field = DebuggerUtils.findField(type, ((PsiField)element).getName());
          if (field != null) {
            return DfaAssistProvider.wrap(((ObjectReference)qualifierValue).getValue(field));
          }
        }
      }
      if (descriptor instanceof ArrayElementDescriptor && qualifierValue instanceof ArrayReference) {
        int index = ((ArrayElementDescriptor)descriptor).getIndex();
        int length = ((ArrayReference)qualifierValue).length();
        if (index >= 0 && index < length) {
          return DfaAssistProvider.wrap(((ArrayReference)qualifierValue).getValue(index));
        }
      }
      return null;
    }
    PsiElement psi = var.getPsiVariable();
    if (psi instanceof PsiClass) {
      // this; probably qualified
      PsiClass currentClass = PsiTreeUtil.getParentOfType(anchor, PsiClass.class);
      return CaptureTraverser.create((PsiClass)psi, currentClass, true).traverse(proxy.thisObject());
    }
    if (psi instanceof PsiLocalVariable || psi instanceof PsiParameter) {
      String varName = ((PsiVariable)psi).getName();
      if (PsiResolveHelper.getInstance(psi.getProject()).resolveReferencedVariable(varName, anchor) != psi) {
        // Another variable with the same name could be tracked by DFA in different code branch but not visible at current code location
        return null;
      }
      LocalVariableProxy variable = proxy.visibleVariableByName(varName);
      if (variable != null) {
        return DfaAssistProvider.wrap(proxy.getVariableValue(variable));
      }
      PsiClass currentClass = PsiTreeUtil.getParentOfType(anchor, PsiClass.class);
      PsiClass varClass = PsiTreeUtil.getParentOfType(psi, PsiClass.class);
      ObjectReference thisRef = CaptureTraverser.create(varClass, currentClass, false)
        .oneLevelLess().traverse(proxy.thisObject());
      if (thisRef != null) {
        ReferenceType type = thisRef.referenceType();
        if (type instanceof ClassType && type.isPrepared()) {
          Field field = DebuggerUtils.findField(type, "val$" + varName);
          if (field != null) {
            return DfaAssistProvider.wrap(thisRef.getValue(field));
          }
        }
      }
    }
    if (psi instanceof PsiField && ((PsiField)psi).hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass psiClass = ((PsiField)psi).getContainingClass();
      if (psiClass != null) {
        String name = psiClass.getQualifiedName();
        if (name != null) {
          ReferenceType type = ContainerUtil.getOnlyItem(proxy.getVirtualMachine().classesByName(name));
          if (type != null && type.isPrepared()) {
            Field field = DebuggerUtils.findField(type, ((PsiField)psi).getName());
            if (field != null && field.isStatic()) {
              return DfaAssistProvider.wrap(type.getValue(field));
            }
          }
        }
      }
    }
    return null;
  }

  @Override
  @NotNull
  public DebuggerDfaListener createListener() {
    return new JavaDebuggerDfaListener();
  }

  @Override
  public @NotNull TypeConstraint constraintFromJvmClassName(@NotNull PsiElement anchor, @NotNull String jvmClassName) {
    PsiClass aClass = DebuggerUtils.findClass(jvmClassName.replace('/', '.'), anchor.getProject(), anchor.getResolveScope());
    return aClass != null ? TypeConstraints.exactClass(aClass) : TypeConstraints.TOP;
  }
}
