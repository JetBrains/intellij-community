// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.ui.CoreAwareIconManager;
import com.intellij.ui.IconManager;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

/**
 * Subclasses are allowed to use bit 17 and higher for flags.
 */
public abstract sealed class RefJavaElementImpl extends RefElementImpl implements RefJavaElement
  permits RefClassImpl, RefFieldImpl, RefFunctionalExpressionImpl, RefMethodImpl, RefParameterImpl {

  private static final int ACCESS_MODIFIER_MASK = 0b11;
  private static final int ACCESS_PRIVATE = 0b00;
  private static final int ACCESS_PROTECTED = 0b01;
  private static final int ACCESS_PACKAGE = 0b10;
  private static final int ACCESS_PUBLIC = 0b11;

  private static final int IS_STATIC_MASK = 0b100;
  private static final int IS_FINAL_MASK = 0b1000;
  private static final int IS_SYNTHETIC_JSP_ELEMENT_MASK = 0b100_00000000;
  private static final int FORBID_PROTECTED_ACCESS_MASK = 0b1000_00000000;

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
    assert (!(psi instanceof UElement));

    if (!(elem.getJavaPsi() instanceof PsiModifierListOwner javaPsi)) throw new AssertionError();
    setAccessModifier(RefJavaUtil.getInstance().getAccessModifier(javaPsi));
    final boolean isSynth = javaPsi instanceof PsiMethod && psi instanceof SyntheticElement || psi instanceof PsiSyntheticClass;
    if (isSynth) {
      setSyntheticJSP(true);
    }

    setIsStatic(elem.isStatic());
    setIsFinal(elem.isFinal());
  }

  RefJavaElementImpl(@NotNull UElement declaration, @NotNull PsiElement psi, @NotNull RefManager manager) {
    super(getName(declaration), psi, manager);
  }

  @Override
  public synchronized @NotNull Collection<RefClass> getOutTypeReferences() {
    return getOwner() instanceof RefJavaElement e ? e.getOutTypeReferences() : Collections.emptySet();
  }

  synchronized void addOutTypeReference(RefClass refClass) {
    if (getOwner() instanceof RefJavaElementImpl e) {
      e.addOutTypeReference(refClass);
    }
  }

  private static @NotNull String getName(@NotNull UElement declaration) {
    PsiElement element = declaration.getJavaPsi();
    if (element instanceof PsiAnonymousClass anonymousClass) {
      PsiClass psiBaseClass = anonymousClass.getBaseClassType().resolve();
      if (psiBaseClass == null) {
        return JavaAnalysisBundle.message("inspection.reference.anonymous.class");
      }
      else {
        return JavaAnalysisBundle.message("inspection.reference.anonymous.name", psiBaseClass.getName());
      }
    }
    if (element instanceof PsiImplicitClass) {
      return JavaAnalysisBundle.message("inspection.reference.implicit.class", element.getContainingFile().getName());
    }

    if (element instanceof PsiSyntheticClass jspClass) {
      return "<" + jspClass.getContainingFile().getName() + ">";
    }

    if (element instanceof PsiMethod) {
      if (element instanceof SyntheticElement) {
        Language language = element.getLanguage();
        if (language.isKindOf("JSP") || language.isKindOf("JSPX")) {
          return JavaAnalysisBundle.message("inspection.reference.jsp.holder.method.anonymous.name");
        }
      }
      return PsiFormatUtil.formatMethod((PsiMethod)element,
                                        PsiSubstitutor.EMPTY,
                                        PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                        PsiFormatUtilBase.SHOW_TYPE);
    }

    if (declaration instanceof ULambdaExpression || declaration instanceof UCallableReferenceExpression) {
      UDeclaration elementDeclaration = UDeclarationKt.getContainingDeclaration(declaration);
      boolean isMethodReference = declaration instanceof UCallableReferenceExpression;
      if (elementDeclaration != null) {
        UAnnotated pDeclaration =
          UastUtils.getParentOfType(elementDeclaration, false, UMethod.class, UClass.class, ULambdaExpression.class, UField.class);
        if (pDeclaration != null && pDeclaration.getSourcePsi() instanceof PsiNamedElement) {
          String name = ((PsiNamedElement)pDeclaration.getSourcePsi()).getName();
          return JavaAnalysisBundle.message(
            isMethodReference ? "inspection.reference.method.reference.name" : "inspection.reference.lambda.name", name);
        }
      }
      return JavaAnalysisBundle.message(
        isMethodReference ? "inspection.reference.default.method.reference.name" : "inspection.reference.default.lambda.name");
    }

    String name = null;
    if (element instanceof PsiNamedElement named) {
      name = named.getName();
    }
    return name == null ? AnalysisBundle.message("inspection.reference.anonymous") : name;
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

  private void setSyntheticJSP(boolean synthetic) {
    setFlag(synthetic, IS_SYNTHETIC_JSP_ELEMENT_MASK);
  }

  @Override
  public synchronized @NotNull String getAccessModifier() {
    long access_id = myFlags & ACCESS_MODIFIER_MASK;
    if (access_id == ACCESS_PRIVATE) return PsiModifier.PRIVATE;
    if (access_id == ACCESS_PUBLIC) return PsiModifier.PUBLIC;
    if (access_id == ACCESS_PACKAGE) return PsiModifier.PACKAGE_LOCAL;
    return PsiModifier.PROTECTED;
  }

  public void setAccessModifier(String visibility) {
    doSetAccessModifier(visibility);
  }

  private synchronized void doSetAccessModifier(@NotNull String visibility) {
    final int accessFlag = switch (visibility) {
      case PsiModifier.PRIVATE -> ACCESS_PRIVATE;
      case PsiModifier.PUBLIC -> ACCESS_PUBLIC;
      case PsiModifier.PACKAGE_LOCAL -> ACCESS_PACKAGE;
      default -> ACCESS_PROTECTED;
    };

    myFlags = myFlags & ~ACCESS_MODIFIER_MASK | accessFlag;
  }

  public boolean isSuspiciousRecursive() {
    return isCalledOnlyFrom(this, new Stack<>());
  }

  private boolean isCalledOnlyFrom(RefJavaElement refElement, Stack<RefJavaElement> callStack) {
    if (callStack.contains(this)) return refElement == this;
    if (getInReferences().isEmpty()) return false;

    if (refElement instanceof RefMethod refMethod) {
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
          !(refCaller instanceof RefJavaElementImpl ref) ||
          !ref.isCalledOnlyFrom(refElement, callStack)) {
        callStack.pop();
        return false;
      }
    }

    callStack.pop();
    return true;
  }

  void addReference(RefElement refWhat,
                    PsiElement psiWhat,
                    UElement from,
                    boolean forWriting,
                    boolean forReading,
                    UExpression expression) {
    PsiElement psiFrom = from.getPsi();
    if (refWhat != null) {
      if (refWhat instanceof RefParameter param) {
        if (forWriting) {
          param.parameterReferenced(true);
        }
        if (forReading) {
          param.parameterReferenced(false);
        }
      }
      addOutReference(refWhat);
      if (refWhat instanceof RefJavaFileImpl file) {
        file.addInReference(this);
        getRefManager().fireNodeMarkedReferenced(psiWhat, psiFrom);
      } else if (refWhat instanceof RefJavaElementImpl refJava) {
        refJava.markReferenced(this, forWriting, forReading, expression);
      }
    } else {
      getRefManager().fireNodeMarkedReferenced(psiWhat, psiFrom);
    }
  }

  protected void markReferenced(@NotNull RefElementImpl refFrom,
                                boolean forWriting,
                                boolean forReading,
                                @Nullable UExpression expressionFrom) {
    addInReference(refFrom);
    setForbidProtectedAccess(refFrom, expressionFrom);
    getRefManager().fireNodeMarkedReferenced(this, refFrom, false, forReading, forWriting, expressionFrom == null ? null : expressionFrom.getSourcePsi());
  }

  void setForbidProtectedAccess(RefElementImpl refFrom, @Nullable UExpression expressionFrom) {
    if (!checkFlag(FORBID_PROTECTED_ACCESS_MASK) &&
        (expressionFrom instanceof UQualifiedReferenceExpression ||
         expressionFrom instanceof UCallExpression call && call.getKind() == UastCallKind.CONSTRUCTOR_CALL)) {
      initializeIfNeeded();
      refFrom.initializeIfNeeded();
      if (RefJavaUtil.getPackage(refFrom) != RefJavaUtil.getPackage(this)) {
        setFlag(true, FORBID_PROTECTED_ACCESS_MASK);
      }
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
  public Icon getIcon(boolean expanded) {
    if (isSyntheticJSP()) {
      final PsiElement element = getPsiElement();
      if (element != null && element.isValid() && IconManager.getInstance() instanceof CoreAwareIconManager manager) {
        return manager.getIcon(element.getContainingFile().getVirtualFile(),
                               Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS, element.getProject());
      }
    }
    return super.getIcon(expanded);
  }
}
