// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.canBeFinal;

import com.intellij.codeInspection.reference.*;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class CanBeFinalAnnotator extends RefGraphAnnotatorEx {
  private final RefManager myManager;
  static long CAN_BE_FINAL_MASK;

  CanBeFinalAnnotator(@NotNull RefManager manager) {
    myManager = manager;
  }

  @Override
  public void initialize(RefManager refManager) {
    CAN_BE_FINAL_MASK = refManager.getLastUsedMask();
  }

  @Override
  public void onInitialize(RefElement refElement) {
    ((RefElementImpl)refElement).setFlag(true, CAN_BE_FINAL_MASK);
  }

  private static void mark(RefElement refElement) {
    if (refElement instanceof RefClass refClass) {
      if (refClass.isEntry()) {
        ((RefClassImpl)refClass).setFlag(false, CAN_BE_FINAL_MASK);
      }
      for (RefClass baseClass : refClass.getBaseClasses()) {
        baseClass.initializeIfNeeded();
        ((RefClassImpl)baseClass).setFlag(false, CAN_BE_FINAL_MASK);
      }
      if (refClass.isAbstract() || refClass.isAnonymous() || refClass.isInterface()) {
        ((RefClassImpl)refClass).setFlag(false, CAN_BE_FINAL_MASK);
      }
    }
    else if (refElement instanceof RefMethod refMethod) {
      RefClass aClass = refMethod.getOwnerClass();
      if (aClass != null) aClass.initializeIfNeeded();
      if (refMethod.isConstructor() || refMethod.isAbstract() || refMethod.isStatic() ||
          PsiModifier.PRIVATE.equals(refMethod.getAccessModifier()) || (aClass != null && aClass.isAnonymous()) ||
          (aClass != null && aClass.isInterface())) {
        ((RefMethodImpl)refMethod).setFlag(false, CAN_BE_FINAL_MASK);
      }
      for (RefMethod superMethod : refMethod.getSuperMethods()) {
        superMethod.initializeIfNeeded();
        ((RefMethodImpl)superMethod).setFlag(false, CAN_BE_FINAL_MASK);
      }
    }
    else if (refElement instanceof RefFieldImpl field) {
      if (field.isImplicitlyWritten()) {
        field.setFlag(false, CAN_BE_FINAL_MASK);
      }
    }
  }

  @Override
  public void onMarkReferenced(RefElement refWhat,
                               RefElement refFrom,
                               boolean referencedFromClassInitializer,
                               boolean forReading,
                               boolean forWriting,
                               PsiElement referenceElement) {
    if (!forWriting) return;
    if (!(refWhat instanceof RefField refField)) return;
    if (refFrom instanceof RefClass && refField.getOwnerClass() != refFrom) {
      ((RefFieldImpl)refWhat).setFlag(false, CAN_BE_FINAL_MASK);
    }
    else if (refField.getUastElement().getUastInitializer() != null) {
      ((RefFieldImpl)refWhat).setFlag(false, CAN_BE_FINAL_MASK);
    }
    else if (!(refFrom instanceof RefMethod) ||
             !((RefMethod)refFrom).isConstructor() ||
             ((RefMethod)refFrom).getOwnerClass() != refField.getOwnerClass() ||
             refField.isStatic()) {
      if (!referencedFromClassInitializer || PsiTreeUtil.getParentOfType(referenceElement, PsiLambdaExpression.class, true) != null) {
        ((RefFieldImpl)refWhat).setFlag(false, CAN_BE_FINAL_MASK);
      }
    }
    else if (PsiTreeUtil.getParentOfType(referenceElement, PsiLambdaExpression.class, true) != null) {
      ((RefFieldImpl)refWhat).setFlag(false, CAN_BE_FINAL_MASK);
    }
  }

  @Override
  public void onReferencesBuild(RefElement refElement) {
    mark(refElement);
    if (refElement instanceof RefClass) {
      final PsiClass psiClass = ObjectUtils.tryCast(refElement.getPsiElement(), PsiClass.class);
      if (psiClass != null) {
        PsiField[] psiFields = psiClass.getFields();
        Set<PsiVariable> allFields = new HashSet<>();
        ContainerUtil.addAll(allFields, psiFields);
        List<PsiVariable> instanceInitializerInitializedFields = new ArrayList<>();
        Set<PsiField> fieldsInitializedInInitializers = null;

        for (PsiClassInitializer initializer : psiClass.getInitializers()) {
          PsiCodeBlock body = initializer.getBody();
          ControlFlow flow;
          try {
            flow = ControlFlowFactory.getInstance(body.getProject())
              .getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
          }
          catch (AnalysisCanceledException e) {
            flow = ControlFlow.EMPTY;
          }
          Collection<PsiVariable> writtenVariables = new ArrayList<>();
          ControlFlowUtil.getWrittenVariables(flow, 0, flow.getSize(), false, writtenVariables);
          if (fieldsInitializedInInitializers == null) {
            fieldsInitializedInInitializers = new HashSet<>();
          }
          for (PsiVariable psiVariable : writtenVariables) {
            if (allFields.contains(psiVariable) && ControlFlowUtil.isVariableDefinitelyAssigned(psiVariable, flow)) {
              if (instanceInitializerInitializedFields.contains(psiVariable)) {
                allFields.remove(psiVariable);
                instanceInitializerInitializedFields.remove(psiVariable);
              }
              else {
                instanceInitializerInitializedFields.add(psiVariable);
              }
              fieldsInitializedInInitializers.add((PsiField)psiVariable);
            }
          }
          for (PsiVariable psiVariable : writtenVariables) {
            if (!instanceInitializerInitializedFields.contains(psiVariable)) {
              allFields.remove(psiVariable);
            }
          }
        }

        for (PsiMethod constructor : psiClass.getConstructors()) {
          PsiCodeBlock body = constructor.getBody();
          if (body != null) {
            ControlFlow flow;
            try {
              flow = ControlFlowFactory.getInstance(body.getProject())
                .getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
            }
            catch (AnalysisCanceledException e) {
              flow = ControlFlow.EMPTY;
            }

            Collection<PsiVariable> writtenVariables = ControlFlowUtil.getWrittenVariables(flow, 0, flow.getSize(), false);
            for (PsiVariable psiVariable : writtenVariables) {
              if (instanceInitializerInitializedFields.contains(psiVariable)) {
                allFields.remove(psiVariable);
                instanceInitializerInitializedFields.remove(psiVariable);
              }
            }
            List<PsiMethod> redirectedConstructors = JavaPsiConstructorUtil.getChainedConstructors(constructor);
            if (redirectedConstructors.isEmpty()) {
              List<PsiVariable> ssaVariables = ControlFlowUtil.getSSAVariables(flow);
              ArrayList<PsiVariable> good = new ArrayList<>(ssaVariables);
              good.addAll(instanceInitializerInitializedFields);
              allFields.retainAll(good);
            }
            else {
              allFields.removeAll(writtenVariables);
            }
          }
        }

        for (PsiField psiField : psiFields) {
          if ((fieldsInitializedInInitializers != null && !fieldsInitializedInInitializers.contains(psiField) ||
               !allFields.contains(psiField)) && psiField.getInitializer() == null) {
            final RefFieldImpl refField = (RefFieldImpl)myManager.getReference(psiField);
            if (refField != null) {
              refField.initializeIfNeeded();
              refField.setFlag(false, CAN_BE_FINAL_MASK);
            }
          }
        }
      }
    }
    else if (refElement instanceof RefMethod refMethod) {
      if (refMethod.isEntry()) {
        ((RefMethodImpl)refMethod).setFlag(false, CAN_BE_FINAL_MASK);
      }
    }
  }
}
