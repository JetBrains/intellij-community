package com.intellij.codeInspection.canBeFinal;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.reference.*;
import com.intellij.j2ee.ejb.EjbRolesUtil;
import com.intellij.j2ee.ejb.role.EjbClassRole;
import com.intellij.j2ee.ejb.role.EjbDeclMethodRole;
import com.intellij.j2ee.ejb.role.EjbImplMethodRole;
import com.intellij.j2ee.ejb.role.EjbMethodRole;
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

  public CanBeFinalAnnotator(final CanBeFinalInspection inspection) {
    myManager = inspection.getRefManager();
  }

  public void initialize(RefElement refElement) {
    refElement.setFlag(true, CAN_BE_FINAL_MASK);
    if (refElement instanceof RefClass) {
      final RefClass refClass = ((RefClass)refElement);
      final PsiClass psiClass = (PsiClass)refClass.getElement();
      EjbClassRole role = EjbRolesUtil.getEjbRole(psiClass);
      if (role != null) {
        refClass.setFlag(false, CAN_BE_FINAL_MASK);
        return;
      }
      if (refClass.isAbstract() || refClass.isAnonymous() || refClass.isInterface()) {
        refClass.setFlag(false, CAN_BE_FINAL_MASK);
        return;
      }
      if (!refClass.isSelfInheritor(psiClass)) {
        for (PsiClass psiSuperClass : psiClass.getSupers()) {
          if (RefUtil.getInstance().belongsToScope(psiSuperClass, myManager)) {
            RefClass refSuperClass = (RefClass)myManager.getReference(psiSuperClass);
            if (refSuperClass != null) {
              refSuperClass.setFlag(false, CAN_BE_FINAL_MASK);
            }
          }
        }
      }
    }
    else if (refElement instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refElement;
      PsiMethod psiMethod = (PsiMethod)refMethod.getElement();
      if (refMethod.isConstructor() || refMethod.isAbstract() || refMethod.isStatic() ||
          PsiModifier.PRIVATE.equals(refMethod.getAccessModifier()) || refMethod.getOwnerClass().isAnonymous() ||
          refMethod.getOwnerClass().isInterface()) {
        refMethod.setFlag(false, CAN_BE_FINAL_MASK);
      }
      if (PsiModifier.PRIVATE.equals(refMethod.getAccessModifier()) && refMethod.getOwner() != null &&
          !(refMethod.getOwnerClass().getOwner() instanceof RefElement)) {
        refMethod.setFlag(false, CAN_BE_FINAL_MASK);
      }
      for (PsiMethod psiSuperMethod : psiMethod.findSuperMethods()) {
        if (RefUtil.getInstance().belongsToScope(psiSuperMethod, myManager)) {
          RefMethod refSuperMethod = (RefMethod)myManager.getReference(psiSuperMethod);
          if (refSuperMethod != null) {
            refSuperMethod.setFlag(false, CAN_BE_FINAL_MASK);
          }
        }
      }
    }
  }

  public void markReferenced(RefElement refWhat, RefElement refFrom, boolean referencedFromClassInitializer) {
    if (!(refWhat instanceof RefField)) return;
    if (!(refFrom instanceof RefMethod) || !((RefMethod)refFrom).isConstructor() || ((PsiField)refWhat.getElement()).hasInitializer()) {
      if (!referencedFromClassInitializer) {
        refWhat.setFlag(false, CAN_BE_FINAL_MASK);
      }
    }
  }

  public void buildReferences(RefElement refElement) {
    if (refElement instanceof RefClass) {
      final PsiClass psiClass = (PsiClass)refElement.getElement();
      if (psiClass != null) {

        EjbClassRole role = EjbRolesUtil.getEjbRole(psiClass);
        if (role != null) {
          refElement.setFlag(false, CAN_BE_FINAL_MASK);
        }

        PsiMethod[] psiMethods = psiClass.getMethods();
        PsiField[] psiFields = psiClass.getFields();

        HashSet<PsiVariable> allFields = new HashSet<PsiVariable>();
        allFields.addAll(Arrays.asList(psiFields));
        ArrayList<PsiVariable> instanceInitializerInitializedFields = new ArrayList<PsiVariable>();
        boolean hasInitializers = false;
        for (PsiClassInitializer initializer : psiClass.getInitializers()) {
          PsiCodeBlock body = initializer.getBody();
          if (body != null) {
            hasInitializers = true;
            ControlFlowAnalyzer analyzer = new ControlFlowAnalyzer(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
            ControlFlow flow;
            try {
              flow = analyzer.buildControlFlow();
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
        }

        for (PsiMethod psiMethod : psiMethods) {
          if (psiMethod.isConstructor()) {
            PsiCodeBlock body = psiMethod.getBody();
            if (body != null) {
              hasInitializers = true;
              ControlFlowAnalyzer analyzer = new ControlFlowAnalyzer(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
              ControlFlow flow;
              try {
                flow = analyzer.buildControlFlow();
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
              List<PsiMethod> redirectedConstructors = HighlightControlFlowUtil.getRedirectedConstructors(psiMethod);
              if ((redirectedConstructors == null || redirectedConstructors.isEmpty())) {
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
            myManager.getReference(psiField).setFlag(false, CAN_BE_FINAL_MASK);
          }
        }

      }
    }
    else if (refElement instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refElement;
      final PsiElement element = refMethod.getElement();
      if (element instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)element;
        if (method != null) {
          EjbClassRole classRole = EjbRolesUtil.getEjbRole(method.getContainingClass());
          if (classRole != null) {
            if (!refMethod.getSuperMethods().isEmpty() || refMethod.isLibraryOverride()) {
              refMethod.setFlag(false, CAN_BE_FINAL_MASK);
            }
            EjbMethodRole role = EjbRolesUtil.getEjbRole(method);
            if (role instanceof EjbDeclMethodRole || role instanceof EjbImplMethodRole) {
              refMethod.setFlag(false, CAN_BE_FINAL_MASK);
            }
          }
        }
      }
    }
  }

  public static void registerMask(final int mask) {
    CAN_BE_FINAL_MASK = mask;
  }
}
