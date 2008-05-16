/*
 * User: anna
 * Date: 20-Dec-2007
 */
package com.intellij.codeInspection.reference;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.util.IconUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.Stack;

public abstract class RefJavaElementImpl extends RefElementImpl implements RefJavaElement {
  private static final HashSet<RefClass> EMPTY_TYPE_REFERENCES_LIST = new HashSet<RefClass>(0);
  private THashSet<RefClass> myOutTypeReferences;
  private static final int ACCESS_MODIFIER_MASK = 0x03;
  private static final int ACCESS_PRIVATE = 0x00;
  private static final int ACCESS_PROTECTED = 0x01;
  private static final int ACCESS_PACKAGE = 0x02;
  private static final int ACCESS_PUBLIC = 0x03;
  private static final int IS_STATIC_MASK = 0x04;
  private static final int IS_FINAL_MASK = 0x08;
  private static final int IS_USES_DEPRECATION_MASK = 0x200;
  private static final int IS_SYNTHETIC_JSP_ELEMENT = 0x400;

  protected RefJavaElementImpl(String name, RefJavaElement owner) {
    super(name, owner);
    String am = owner.getAccessModifier();
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

    myFlags = ((myFlags >> 2) << 2) | access_id;

    final boolean synthOwner = owner.isSyntheticJSP();
    if (synthOwner) {
      setSyntheticJSP(true);
    }
  }

  protected RefJavaElementImpl(PsiFile file, RefManager manager) {
    super(file, manager);
  }

  protected RefJavaElementImpl(PsiModifierListOwner elem, RefManager manager) {
    super(getName(elem), elem, manager);

    setAccessModifier(RefJavaUtil.getInstance().getAccessModifier(elem));
    final boolean isSynth = elem instanceof JspHolderMethod || elem instanceof JspClass;
    if (isSynth) {
      setSyntheticJSP(true);
    }

    setIsStatic(elem.hasModifierProperty(PsiModifier.STATIC));
    setIsFinal(elem.hasModifierProperty(PsiModifier.FINAL));
  }

  protected SmartPsiElementPointer<PsiElement> createPointer(final PsiElement element, final RefManager manager) {
    if (element instanceof JspHolderMethod || element instanceof JspClass) {
      return SmartPointerManager.getInstance(manager.getProject()).createSmartPsiElementPointer(element);
    }

    return super.createPointer(element, manager);
  }

  @NotNull
  public Collection<RefClass> getOutTypeReferences() {
    if (myOutTypeReferences == null){
      return EMPTY_TYPE_REFERENCES_LIST;
    }
    return myOutTypeReferences;
  }

  public void addOutTypeRefernce(RefClass refClass){
    if (myOutTypeReferences == null){
      myOutTypeReferences = new THashSet<RefClass>();
    }
    myOutTypeReferences.add(refClass);
  }

  public static String getName(PsiElement element) {
   if (element instanceof PsiAnonymousClass) {
     PsiAnonymousClass psiAnonymousClass = (PsiAnonymousClass)element;
     PsiClass psiBaseClass = psiAnonymousClass.getBaseClassType().resolve();
     return InspectionsBundle.message("inspection.reference.anonymous.name", (psiBaseClass != null ? psiBaseClass.getQualifiedName() : ""));
   }

   if (element instanceof JspClass) {
     final JspClass jspClass = (JspClass)element;
     final PsiFile jspxFile = jspClass.getContainingFile();
     return "<" + jspxFile.getName() + ">";
   }

   if (element instanceof JspHolderMethod) {
     return InspectionsBundle.message("inspection.reference.jsp.holder.method.anonymous.name");
   }

   String name = null;
   if (element instanceof PsiNamedElement) {
     name = ((PsiNamedElement)element).getName();
   }

   return name == null ? InspectionsBundle.message("inspection.reference.anonymous") : name;
 }

  public boolean isFinal() {
    return checkFlag(IS_FINAL_MASK);
  }

  public boolean isStatic() {
    return checkFlag(IS_STATIC_MASK);
  }

  public void setIsStatic(boolean isStatic) {
    setFlag(isStatic, IS_STATIC_MASK);
  }

  public boolean isUsesDeprecatedApi() {
    return checkFlag(IS_USES_DEPRECATION_MASK);
  }

  public void setUsesDeprecatedApi(boolean usesDeprecatedApi) {
    setFlag(usesDeprecatedApi, IS_USES_DEPRECATION_MASK);
  }

  public void setIsFinal(boolean isFinal) {
    setFlag(isFinal, IS_FINAL_MASK);
  }

  public void setReachable(boolean reachable) {
    setFlag(reachable, IS_REACHABLE_MASK);
  }

  public boolean isSyntheticJSP() {
    return checkFlag(IS_SYNTHETIC_JSP_ELEMENT);
  }

  public void setSyntheticJSP(boolean b) {
    setFlag(b, IS_SYNTHETIC_JSP_ELEMENT);
  }

  @Nullable
  public String getAccessModifier() {
    long access_id = myFlags & ACCESS_MODIFIER_MASK;
    if (access_id == ACCESS_PRIVATE) return PsiModifier.PRIVATE;
    if (access_id == ACCESS_PUBLIC) return PsiModifier.PUBLIC;
    if (access_id == ACCESS_PACKAGE) return PsiModifier.PACKAGE_LOCAL;
    return PsiModifier.PROTECTED;
  }

  public void setAccessModifier(String am) {
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

    myFlags = ((myFlags >> 2) << 2) | access_id;
  }

  public boolean isSuspiciousRecursive() {
    return isCalledOnlyFrom(this, new Stack<RefJavaElement>());
  }

  private boolean isCalledOnlyFrom(RefJavaElement refElement, Stack<RefJavaElement> callStack) {
    if (callStack.contains(this)) return refElement == this;
    if (getInReferences().size() == 0) return false;

    if (refElement instanceof RefMethod) {
      RefMethod refMethod = (RefMethod) refElement;
      for (RefMethod refSuper : refMethod.getSuperMethods()) {
        if (refSuper.getInReferences().size() > 0) return false;
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
      if (!((RefElementImpl)refCaller).isSuspicious() || !((RefJavaElementImpl)refCaller).isCalledOnlyFrom(refElement, callStack)) {
        callStack.pop();
        return false;
      }
    }

    callStack.pop();
    return true;
  }

  public void addReference(RefElement refWhat, PsiElement psiWhat, PsiElement psiFrom, boolean forWriting, boolean forReading, PsiReferenceExpression expression) {
    if (refWhat != null) {
      if (refWhat instanceof RefParameter) {
        if (forWriting) {
          ((RefParameterImpl)refWhat).parameterReferenced(true);
        }
        if (forReading) {
          ((RefParameterImpl)refWhat).parameterReferenced(false);
        }
      }
      addOutReference(refWhat);
      ((RefJavaElementImpl)refWhat).markReferenced(this, psiFrom, psiWhat, forWriting, forReading, expression);
    }
  }

  protected void markReferenced(final RefElementImpl refFrom, PsiElement psiFrom, PsiElement psiWhat, final boolean forWriting, boolean forReading, PsiReferenceExpression expressionFrom) {
    addInReference(refFrom);
    getRefManager().fireNodeMarkedReferenced(this, refFrom, false, forReading, forWriting);
  }

  protected RefJavaManager getRefJavaManager() {
    return getRefManager().getExtension(RefJavaManager.MANAGER);
  }

  public void referenceRemoved() {
    super.referenceRemoved();
    if (isEntry()) {
      getRefJavaManager().getEntryPointsManager().removeEntryPoint(this);
    }
  }

  public Icon getIcon(final boolean expanded) {
    if (isSyntheticJSP()) {
      final PsiElement element = getElement();
      if (element != null) {
        return IconUtil.getIcon(element.getContainingFile().getVirtualFile(),
                                Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS, element.getProject());
      }
    }
    return super.getIcon(expanded);
  }
}