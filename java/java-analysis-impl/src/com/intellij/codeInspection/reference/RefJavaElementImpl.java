// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.reference;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.util.IconUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.Stack;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.*;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public abstract class RefJavaElementImpl extends RefElementImpl implements RefJavaElement {
  private Set<RefClass> myOutTypeReferences; // guarded by this
  private static final int ACCESS_MODIFIER_MASK = 0x03;
  private static final int ACCESS_PRIVATE = 0x00;
  private static final int ACCESS_PROTECTED = 0x01;
  private static final int ACCESS_PACKAGE = 0x02;
  private static final int ACCESS_PUBLIC = 0x03;

  private static final int IS_STATIC_MASK = 0x04;
  private static final int IS_FINAL_MASK = 0x08;
  private static final int IS_SYNTHETIC_JSP_ELEMENT_MASK = 0x400;
  private static final int FORBID_PROTECTED_ACCESS_MASK = 0x800;

  protected RefJavaElementImpl(@NotNull String name, @NotNull RefJavaElement owner) {
    super(name, owner);
    String am = owner.getAccessModifier();
    doSetAccessModifier(am);

    final boolean synthOwner = owner.isSyntheticJSP();
    if (synthOwner) {
      setSyntheticJSP(true);
    }
  }

  protected RefJavaElementImpl(UDeclaration elem, PsiElement psi, RefManager manager) {
    super(getName(elem), psi, manager);

    PsiModifierListOwner javaPsi = ObjectUtils.notNull(ObjectUtils.tryCast(elem.getJavaPsi(), PsiModifierListOwner.class));
    setAccessModifier(RefJavaUtil.getInstance().getAccessModifier(javaPsi));
    final boolean isSynth = javaPsi instanceof PsiMethod && psi instanceof SyntheticElement  || psi instanceof PsiSyntheticClass;
    if (isSynth) {
      setSyntheticJSP(true);
    }

    setIsStatic(elem.isStatic());
    setIsFinal(elem.isFinal());
  }

  @Override
  @NotNull
  public synchronized Collection<RefClass> getOutTypeReferences() {
    return ObjectUtils.notNull(myOutTypeReferences, Collections.emptySet());
  }

  synchronized void addOutTypeReference(RefClass refClass){
    if (myOutTypeReferences == null){
      myOutTypeReferences = new THashSet<>();
    }
    myOutTypeReferences.add(refClass);
  }

  @NotNull
  public static String getName(UDeclaration declaration) {
    PsiElement element = declaration.getJavaPsi();
    if (element instanceof PsiAnonymousClass) {
     PsiAnonymousClass psiAnonymousClass = (PsiAnonymousClass)element;
     PsiClass psiBaseClass = psiAnonymousClass.getBaseClassType().resolve();
     if (psiBaseClass == null) {
       return "anonymous class";
     } else {
       return InspectionsBundle.message("inspection.reference.anonymous.name", psiBaseClass.getName());
     }
   }

   if (element instanceof PsiSyntheticClass) {
     final PsiSyntheticClass jspClass = (PsiSyntheticClass)element;
     final PsiFile jspxFile = jspClass.getContainingFile();
     return "<" + jspxFile.getName() + ">";
   }

   if (element instanceof PsiMethod && element instanceof SyntheticElement ) {
     return InspectionsBundle.message("inspection.reference.jsp.holder.method.anonymous.name");
   }

   String name = null;
   if (element instanceof PsiNamedElement) {
     name = ((PsiNamedElement)element).getName();
   }
   return name == null ? InspectionsBundle.message("inspection.reference.anonymous") : name;
 }

  @Override
  public boolean isFinal() {
    return checkFlag(IS_FINAL_MASK);
  }

  @Override
  public boolean isStatic() {
    return checkFlag(IS_STATIC_MASK);
  }

  void setIsStatic(boolean isStatic) {
    setFlag(isStatic, IS_STATIC_MASK);
  }

  void setIsFinal(boolean isFinal) {
    setFlag(isFinal, IS_FINAL_MASK);
  }


  @Override
  public boolean isSyntheticJSP() {
    return checkFlag(IS_SYNTHETIC_JSP_ELEMENT_MASK);
  }

  private void setSyntheticJSP(boolean b) {
    setFlag(b, IS_SYNTHETIC_JSP_ELEMENT_MASK);
  }

  @NotNull
  @Override
  public String getAccessModifier() {
    long access_id = myFlags & ACCESS_MODIFIER_MASK;
    if (access_id == ACCESS_PRIVATE) return PsiModifier.PRIVATE;
    if (access_id == ACCESS_PUBLIC) return PsiModifier.PUBLIC;
    if (access_id == ACCESS_PACKAGE) return PsiModifier.PACKAGE_LOCAL;
    return PsiModifier.PROTECTED;
  }

  public void setAccessModifier(String am) {
    doSetAccessModifier(am);
  }

  private void doSetAccessModifier(@NotNull String am) {
    final int access_id;

    if (PsiModifier.PRIVATE.equals(am)) {
      access_id = ACCESS_PRIVATE;
    }
    else if (PsiModifier.PUBLIC.equals(am)) {
      access_id = ACCESS_PUBLIC;
    }
    else if (PsiModifier.PACKAGE_LOCAL.equals(am)) {
      access_id = ACCESS_PACKAGE;
    }
    else {
      access_id = ACCESS_PROTECTED;
    }

    myFlags = myFlags & ~0x3 | access_id;
  }

  public boolean isSuspiciousRecursive() {
    return isCalledOnlyFrom(this, new Stack<>());
  }

  private boolean isCalledOnlyFrom(RefJavaElement refElement, Stack<RefJavaElement> callStack) {
    if (callStack.contains(this)) return refElement == this;
    if (getInReferences().isEmpty()) return false;

    if (refElement instanceof RefMethod) {
      RefMethod refMethod = (RefMethod) refElement;
      for (RefMethod refSuper : refMethod.getSuperMethods()) {
        if (!refSuper.getInReferences().isEmpty()) return false;
      }
      if (refMethod.isConstructor()){
        boolean unreachable = true;
        for (RefElement refOut : refMethod.getOutReferences()){
          unreachable &= !refOut.isReachable();
        }
        if (unreachable) return true;
      }
    }

    callStack.push(this);
    for (RefElement refCaller : getInReferences()) {
      if (!((RefElementImpl)refCaller).isSuspicious() ||
          !(refCaller instanceof RefJavaElementImpl) ||
          !((RefJavaElementImpl)refCaller).isCalledOnlyFrom(refElement, callStack)) {
        callStack.pop();
        return false;
      }
    }

    callStack.pop();
    return true;
  }

  void addReference(RefElement refWhat,
                    PsiElement psiWhat,
                    UDeclaration from,
                    boolean forWriting,
                    boolean forReading,
                    UExpression expression) {
    PsiElement psiFrom = from.getPsi();
    if (refWhat != null) {
      if (refWhat instanceof RefParameter) {
        if (forWriting) {
          ((RefParameter)refWhat).parameterReferenced(true);
        }
        if (forReading) {
          ((RefParameter)refWhat).parameterReferenced(false);
        }
      }
      addOutReference(refWhat);
      if (refWhat instanceof RefJavaFileImpl) {
        ((RefJavaFileImpl)refWhat).addInReference(this);
        getRefManager().fireNodeMarkedReferenced(psiWhat, psiFrom);
      } else if (refWhat instanceof RefJavaElementImpl) {
        ((RefJavaElementImpl)refWhat).markReferenced(this, psiFrom, psiWhat, forWriting, forReading, expression);
      }
    } else {
      if (psiWhat instanceof PsiMethod) {
        markEnumUsedIfValuesMethod((PsiMethod)psiWhat, expression, psiFrom);
      }
      getRefManager().fireNodeMarkedReferenced(psiWhat, psiFrom);
    }
  }

  protected void markReferenced(final RefElementImpl refFrom, PsiElement psiFrom, PsiElement psiWhat, final boolean forWriting, boolean forReading, UExpression expressionFrom) {
    addInReference(refFrom);
    setForbidProtectedAccess(refFrom, expressionFrom);
    getRefManager().fireNodeMarkedReferenced(this, refFrom, false, forReading, forWriting, expressionFrom == null ? null : expressionFrom.getSourcePsi());
  }

  void setForbidProtectedAccess(RefElementImpl refFrom, UExpression expressionFrom) {
    if (!checkFlag(FORBID_PROTECTED_ACCESS_MASK) &&
        (expressionFrom instanceof UQualifiedReferenceExpression || 
         expressionFrom instanceof UCallExpression && ((UCallExpression)expressionFrom).getKind() == UastCallKind.CONSTRUCTOR_CALL) && 
        RefJavaUtil.getPackage(refFrom) != RefJavaUtil.getPackage(this)) {
      setFlag(true, FORBID_PROTECTED_ACCESS_MASK);
    }
  }

  public boolean isProtectedAccessForbidden() {
    return checkFlag(FORBID_PROTECTED_ACCESS_MASK);
  }
  
  RefJavaManager getRefJavaManager() {
    return getRefManager().getExtension(RefJavaManager.MANAGER);
  }

  @Override
  public void referenceRemoved() {
    super.referenceRemoved();
    if (isEntry()) {
      getRefJavaManager().getEntryPointsManager().removeEntryPoint(this);
    }
  }

  @Override
  public Icon getIcon(final boolean expanded) {
    if (isSyntheticJSP()) {
      final PsiElement element = getPsiElement();
      if (element != null && element.isValid()) {
        return IconUtil.getIcon(element.getContainingFile().getVirtualFile(),
                                Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS, element.getProject());
      }
    }
    return super.getIcon(expanded);
  }

  private void markEnumUsedIfValuesMethod(PsiMethod psiWhat, UExpression expression, PsiElement psiFrom) {
    //TODO support kotlin enums
    final PsiClass containingClass = psiWhat.getContainingClass();
    if (containingClass != null && containingClass.isEnum() && "values".equals(psiWhat.getName())) {
      for (PsiField enumConstant : containingClass.getFields()) {
        if (enumConstant instanceof PsiEnumConstant) {
          final RefJavaElementImpl enumConstantReference = (RefJavaElementImpl)getRefManager().getReference(enumConstant);
          if (enumConstantReference != null) {
            addOutReference(enumConstantReference);
            enumConstantReference.markReferenced(this, psiFrom, enumConstant, false, true, expression);
          }
        }
      }
    }
  }
}
