package com.intellij.codeInspection.canBeFinal;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.reference.*;
import com.intellij.javaee.ejb.role.*;
import com.intellij.javaee.ejb.EjbHelper;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * User: anna
 * Date: 27-Dec-2005
 */
class CanBeFinalAnnotator extends RefGraphAnnotator {
  private RefManager myManager;
  public static int CAN_BE_FINAL_MASK;

  public CanBeFinalAnnotator(RefManager manager) {
    myManager = manager;
  }

  public void onInitialize(RefElement refElement) {
    ((RefElementImpl)refElement).setFlag(true, CAN_BE_FINAL_MASK);
    if (refElement instanceof RefClass) {
      final RefClass refClass = ((RefClass)refElement);
      final PsiClass psiClass = refClass.getElement();
      EjbClassRole role = EjbRolesUtil.getEjbRolesUtil().getEjbRole(psiClass);
      if (role != null) {
        ((RefClassImpl)refClass).setFlag(false, CAN_BE_FINAL_MASK);
        return;
      }
      if (refClass.isAbstract() || refClass.isAnonymous() || refClass.isInterface()) {
        ((RefClassImpl)refClass).setFlag(false, CAN_BE_FINAL_MASK);
        return;
      }
      if (!refClass.isSelfInheritor(psiClass)) {
        for (PsiClass psiSuperClass : psiClass.getSupers()) {
          if (RefUtil.getInstance().belongsToScope(psiSuperClass, myManager)) {
            RefClass refSuperClass = (RefClass)myManager.getReference(psiSuperClass);
            if (refSuperClass != null) {
              ((RefClassImpl)refSuperClass).setFlag(false, CAN_BE_FINAL_MASK);
            }
          }
        }
      }
    }
    else if (refElement instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refElement;
      final PsiElement element = refMethod.getElement();
      if (element instanceof PsiMethod) {
        PsiMethod psiMethod = (PsiMethod)element;
        if (refMethod.isConstructor() || refMethod.isAbstract() || refMethod.isStatic() ||
            PsiModifier.PRIVATE.equals(refMethod.getAccessModifier()) || refMethod.getOwnerClass().isAnonymous() ||
            refMethod.getOwnerClass().isInterface()) {
          ((RefMethodImpl)refMethod).setFlag(false, CAN_BE_FINAL_MASK);
        }
        if (PsiModifier.PRIVATE.equals(refMethod.getAccessModifier()) && refMethod.getOwner() != null &&
            !(refMethod.getOwnerClass().getOwner() instanceof RefElement)) {
          ((RefMethodImpl)refMethod).setFlag(false, CAN_BE_FINAL_MASK);
        }
        for (PsiMethod psiSuperMethod : psiMethod.findSuperMethods()) {
          if (RefUtil.getInstance().belongsToScope(psiSuperMethod, myManager)) {
            RefMethod refSuperMethod = (RefMethod)myManager.getReference(psiSuperMethod);
            if (refSuperMethod != null) {
              ((RefMethodImpl)refSuperMethod).setFlag(false, CAN_BE_FINAL_MASK);
            }
          }
        }
      }
    }
  }

  public void onMarkReferenced(RefElement refWhat, RefElement refFrom, boolean referencedFromClassInitializer) {
    if (!(refWhat instanceof RefField)) return;
    if (!(refFrom instanceof RefMethod) || !((RefMethod)refFrom).isConstructor() || ((PsiField)refWhat.getElement()).hasInitializer()) {
      if (!referencedFromClassInitializer) {
        ((RefFieldImpl)refWhat).setFlag(false, CAN_BE_FINAL_MASK);
      }
    }
  }

  public void onReferencesBuild(RefElement refElement) {
    if (refElement instanceof RefClass) {
      final PsiClass psiClass = (PsiClass)refElement.getElement();
      if (psiClass != null) {

        EjbClassRole role = EjbRolesUtil.getEjbRolesUtil().getEjbRole(psiClass);
        if (role != null) {
          ((RefClassImpl)refElement).setFlag(false, CAN_BE_FINAL_MASK);
        }

        PsiMethod[] psiMethods = psiClass.getMethods();
        PsiField[] psiFields = psiClass.getFields();

        HashSet<PsiVariable> allFields = new HashSet<PsiVariable>();
        allFields.addAll(Arrays.asList(psiFields));
        ArrayList<PsiVariable> instanceInitializerInitializedFields = new ArrayList<PsiVariable>();
        boolean hasInitializers = false;
        for (PsiClassInitializer initializer : psiClass.getInitializers()) {
          PsiCodeBlock body = initializer.getBody();
          hasInitializers = true;
          ControlFlow flow;
          try {
            flow = ControlFlowFactory.getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
          }
          catch (AnalysisCanceledException e) {
            flow = ControlFlow.EMPTY;
          }
          PsiVariable[] ssaVariables = ControlFlowUtil.getSSAVariables(flow, false);
          PsiVariable[] writtenVariables = ControlFlowUtil.getWrittenVariables(flow, 0, flow.getSize(), false);
          for (int j = 0; j < ssaVariables.length; j++) {
            PsiVariable psiVariable = writtenVariables[j];
            if (allFields.contains(psiVariable)) {
              if (instanceInitializerInitializedFields.contains(psiVariable)) {
                allFields.remove(psiVariable);
                instanceInitializerInitializedFields.remove(psiVariable);
              }
              else {
                instanceInitializerInitializedFields.add(psiVariable);
              }
            }
          }
          for (PsiVariable psiVariable : writtenVariables) {
            if (!instanceInitializerInitializedFields.contains(psiVariable)) {
              allFields.remove(psiVariable);
            }
          }
        }

        for (PsiMethod psiMethod : psiMethods) {
          if (psiMethod.isConstructor()) {
            PsiCodeBlock body = psiMethod.getBody();
            if (body != null) {
              hasInitializers = true;
              ControlFlow flow;
              try {
                flow = ControlFlowFactory.getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
              }
              catch (AnalysisCanceledException e) {
                flow = ControlFlow.EMPTY;
              }

              PsiVariable[] writtenVariables = ControlFlowUtil.getWrittenVariables(flow, 0, flow.getSize(), false);
              for (PsiVariable psiVariable : writtenVariables) {
                if (instanceInitializerInitializedFields.contains(psiVariable)) {
                  allFields.remove(psiVariable);
                  instanceInitializerInitializedFields.remove(psiVariable);
                }
              }
              List<PsiMethod> redirectedConstructors = HighlightControlFlowUtil.getChainedConstructors(psiMethod);
              if (redirectedConstructors == null || redirectedConstructors.isEmpty()) {
                PsiVariable[] ssaVariables = ControlFlowUtil.getSSAVariables(flow, false);
                ArrayList<PsiVariable> good = new ArrayList<PsiVariable>(Arrays.asList(ssaVariables));
                good.addAll(instanceInitializerInitializedFields);
                allFields.retainAll(good);
              }
              else {
                allFields.removeAll(Arrays.asList(writtenVariables));
              }
            }
          }
        }

        for (PsiField psiField : psiFields) {
          if ((!hasInitializers || !allFields.contains(psiField)) && psiField.getInitializer() == null) {
            ((RefFieldImpl)myManager.getReference(psiField)).setFlag(false, CAN_BE_FINAL_MASK);
          }
        }

      }
    }
    else if (refElement instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refElement;
      final PsiElement element = refMethod.getElement();
      if (element instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)element;
        final EjbHelper helper = EjbHelper.getEjbHelper();
        EjbClassRole classRole = helper.getEjbRole(method.getContainingClass());
        if (classRole != null) {
          if (!refMethod.hasSuperMethods()) {
            ((RefMethodImpl)refMethod).setFlag(false, CAN_BE_FINAL_MASK);
          }
          EjbMethodRole role = helper.getEjbRole(method);
          if (role instanceof EjbDeclMethodRole || role instanceof EjbImplMethodRole) {
            ((RefMethodImpl)refMethod).setFlag(false, CAN_BE_FINAL_MASK);
          }
        }
      }
    }
  }

  public void setMask(int mask) {
    CAN_BE_FINAL_MASK = mask;
  }
}
