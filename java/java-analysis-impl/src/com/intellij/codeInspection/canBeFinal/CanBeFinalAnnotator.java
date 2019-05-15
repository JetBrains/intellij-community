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
package com.intellij.codeInspection.canBeFinal;

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInspection.reference.*;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UTypeReferenceExpression;

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
    if (refElement instanceof RefClass) {
      final RefClass refClass = (RefClass)refElement;
      final UClass psiClass = refClass.getUastElement();
      if (refClass.isEntry()) {
        ((RefClassImpl)refClass).setFlag(false, CAN_BE_FINAL_MASK);
        return;
      }
      if (psiClass != null && !refClass.isSelfInheritor(psiClass)) {
        for (UTypeReferenceExpression superRef : psiClass.getUastSuperTypes()) {
          PsiElement psi = PsiTypesUtil.getPsiClass(superRef.getType());
          if (myManager.belongsToScope(psi)) {
            RefClass refSuperClass = (RefClass)myManager.getReference(psi);
            if (refSuperClass != null) {
              ((RefClassImpl)refSuperClass).setFlag(false, CAN_BE_FINAL_MASK);
            }
          }
        }
      }
      if (refClass.isAbstract() || refClass.isAnonymous() || refClass.isInterface()) {
        ((RefClassImpl)refClass).setFlag(false, CAN_BE_FINAL_MASK);
      }
    }
    else if (refElement instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refElement;
      final PsiElement element = refMethod.getPsiElement();
      if (element instanceof PsiMethod) {
        PsiMethod psiMethod = (PsiMethod)element;
        RefClass aClass = refMethod.getOwnerClass();
        if (refMethod.isConstructor() || refMethod.isAbstract() || refMethod.isStatic() ||
            PsiModifier.PRIVATE.equals(refMethod.getAccessModifier()) || (aClass != null && aClass.isAnonymous()) ||
            (aClass != null && aClass.isInterface())) {
          ((RefMethodImpl)refMethod).setFlag(false, CAN_BE_FINAL_MASK);
        }
        if (PsiModifier.PRIVATE.equals(refMethod.getAccessModifier()) && refMethod.getOwner() != null &&
            !(aClass != null && aClass.getOwner() instanceof RefElement)) {
          ((RefMethodImpl)refMethod).setFlag(false, CAN_BE_FINAL_MASK);
        }
        for (PsiMethod psiSuperMethod : psiMethod.findSuperMethods()) {
          if (myManager.belongsToScope(psiSuperMethod)) {
            RefMethod refSuperMethod = (RefMethod)myManager.getReference(psiSuperMethod);
            if (refSuperMethod != null) {
              ((RefMethodImpl)refSuperMethod).setFlag(false, CAN_BE_FINAL_MASK);
            }
          }
        }
      }
    }
    else if (refElement instanceof RefField) {
      final PsiElement element = refElement.getPsiElement();
      if (RefUtil.isImplicitWrite(element)) {
        ((RefElementImpl)refElement).setFlag(false, CAN_BE_FINAL_MASK);
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
    if (!(refWhat instanceof RefField)) return;
    if (!(refFrom instanceof RefMethod) ||
        !((RefMethod)refFrom).isConstructor() ||
        ((RefField)refWhat).getUastElement().getUastInitializer() != null ||
        ((RefMethod)refFrom).getOwnerClass() != ((RefField)refWhat).getOwnerClass() ||
        ((RefField)refWhat).isStatic()) {
      if (forWriting &&
          !(referencedFromClassInitializer && PsiTreeUtil.getParentOfType(referenceElement, PsiLambdaExpression.class, true) == null)) {
        ((RefFieldImpl)refWhat).setFlag(false, CAN_BE_FINAL_MASK);
      }
    }
    else if (forWriting && PsiTreeUtil.getParentOfType(referenceElement, PsiLambdaExpression.class, true) != null) {
      ((RefFieldImpl)refWhat).setFlag(false, CAN_BE_FINAL_MASK);
    }
  }

  @Override
  public void onReferencesBuild(RefElement refElement) {
    if (refElement instanceof RefClass) {
      final PsiClass psiClass = ObjectUtils.tryCast(refElement.getPsiElement(), PsiClass.class);
      if (psiClass != null) {

        if (refElement.isEntry()) {
          ((RefClassImpl)refElement).setFlag(false, CAN_BE_FINAL_MASK);
        }


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
            List<PsiMethod> redirectedConstructors = JavaHighlightUtil.getChainedConstructors(constructor);
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
              refField.setFlag(false, CAN_BE_FINAL_MASK);
            }
          }
        }

      }
    }
    else if (refElement instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refElement;
      if (refMethod.isEntry()) {
        ((RefMethodImpl)refMethod).setFlag(false, CAN_BE_FINAL_MASK);
      }
    }
  }
}
