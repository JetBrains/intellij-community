/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInspection.reference;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.util.IconUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.Stack;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private static final int IS_USES_DEPRECATION_MASK = 0x200;
  private static final int IS_SYNTHETIC_JSP_ELEMENT_MASK = 0x400;
  private static final int IS_USED_QUALIFIED_OUTSIDE_PACKAGE_MASK = 0x800;

  protected RefJavaElementImpl(@NotNull String name, @NotNull RefJavaElement owner) {
    super(name, owner);
    String am = owner.getAccessModifier();
    doSetAccessModifier(am);

    final boolean synthOwner = owner.isSyntheticJSP();
    if (synthOwner) {
      setSyntheticJSP(true);
    }
  }

  protected RefJavaElementImpl(PsiModifierListOwner elem, RefManager manager) {
    super(getName(elem), elem, manager);

    setAccessModifier(RefJavaUtil.getInstance().getAccessModifier(elem));
    final boolean isSynth = elem instanceof PsiMethod && elem instanceof SyntheticElement  || elem instanceof PsiSyntheticClass;
    if (isSynth) {
      setSyntheticJSP(true);
    }

    setIsStatic(elem.hasModifierProperty(PsiModifier.STATIC));
    setIsFinal(elem.hasModifierProperty(PsiModifier.FINAL));
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
  private static String getName(PsiElement element) {
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

  @Override
  public boolean isUsesDeprecatedApi() {
    return checkFlag(IS_USES_DEPRECATION_MASK);
  }

  void setUsesDeprecatedApi(boolean usesDeprecatedApi) {
    setFlag(usesDeprecatedApi, IS_USES_DEPRECATION_MASK);
  }

  void setIsFinal(boolean isFinal) {
    setFlag(isFinal, IS_FINAL_MASK);
  }

  public void setReachable(boolean reachable) {
    setFlag(reachable, IS_REACHABLE_MASK);
  }

  @Override
  public boolean isSyntheticJSP() {
    return checkFlag(IS_SYNTHETIC_JSP_ELEMENT_MASK);
  }

  private void setSyntheticJSP(boolean b) {
    setFlag(b, IS_SYNTHETIC_JSP_ELEMENT_MASK);
  }

  @Override
  @Nullable
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
                    PsiElement psiFrom,
                    boolean forWriting,
                    boolean forReading,
                    PsiReferenceExpression expression) {
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
      ((RefJavaElementImpl)refWhat).markReferenced(this, psiFrom, psiWhat, forWriting, forReading, expression);
    } else {
      if (psiWhat instanceof PsiMethod) {
        final PsiClass containingClass = ((PsiMethod)psiWhat).getContainingClass();
        if (containingClass != null && containingClass.isEnum() && "values".equals(((PsiMethod)psiWhat).getName())) {
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
      getRefManager().fireNodeMarkedReferenced(psiWhat, psiFrom);
    }
  }

  protected void markReferenced(final RefElementImpl refFrom, PsiElement psiFrom, PsiElement psiWhat, final boolean forWriting, boolean forReading, PsiReferenceExpression expressionFrom) {
    addInReference(refFrom);
    setUsedQualifiedOutsidePackageFlag(refFrom, expressionFrom);
    getRefManager().fireNodeMarkedReferenced(this, refFrom, false, forReading, forWriting);
  }

  void setUsedQualifiedOutsidePackageFlag(RefElementImpl refFrom, PsiReferenceExpression expressionFrom) {
    if (!checkFlag(IS_USED_QUALIFIED_OUTSIDE_PACKAGE_MASK) && expressionFrom != null &&
        expressionFrom.isQualified() && RefJavaUtil.getPackage(refFrom) != RefJavaUtil.getPackage(this)) {
      setFlag(true, IS_USED_QUALIFIED_OUTSIDE_PACKAGE_MASK);
    }
  }

  public boolean isUsedQualifiedOutsidePackage() {
    return checkFlag(IS_USED_QUALIFIED_OUTSIDE_PACKAGE_MASK);
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
      final PsiElement element = getElement();
      if (element != null && element.isValid()) {
        return IconUtil.getIcon(element.getContainingFile().getVirtualFile(),
                                Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS, element.getProject());
      }
    }
    return super.getIcon(expanded);
  }
}
