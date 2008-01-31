package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TargetElementUtil extends TargetElementUtilBase {
  public static final int NEW_AS_CONSTRUCTOR = 0x04;
  public static final int THIS_ACCEPTED = 0x10;
  public static final int SUPER_ACCEPTED = 0x20;

  @Override
  public int getAllAcepted() {
    return super.getAllAcepted() | NEW_AS_CONSTRUCTOR | THIS_ACCEPTED | SUPER_ACCEPTED;
  }

  @Nullable
  @Override
  public PsiElement findTargetElement(final Editor editor, final int flags, final int offset) {
    final PsiElement element = super.findTargetElement(editor, flags, offset);
    if (element instanceof PsiKeyword) {
      if (element.getParent() instanceof PsiThisExpression) {
        if ((flags & TargetElementUtil.THIS_ACCEPTED) == 0) return null;
        PsiType type = ((PsiThisExpression)element.getParent()).getType();
        if (!(type instanceof PsiClassType)) return null;
        return ((PsiClassType)type).resolve();
      }

      if (element.getParent() instanceof PsiSuperExpression) {
        if ((flags & TargetElementUtil.SUPER_ACCEPTED) == 0) return null;
        PsiType type = ((PsiSuperExpression)element.getParent()).getType();
        if (!(type instanceof PsiClassType)) return null;
        return ((PsiClassType)type).resolve();
      }
    }
    return element;
  }

  @Nullable
  protected PsiElement getReferenceOrReferencedElement(PsiFile file, Editor editor, int flags, int offset) {
    PsiReference ref = TargetElementUtilBase.findReference(editor, offset);
    if (ref == null) return null;
    PsiManager manager = file.getManager();

    final PsiElement referenceElement = ref.getElement();
    PsiElement refElement;
    if (ref instanceof PsiJavaReference) {
      refElement = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    }
    else {
      refElement = ref.resolve();
    }

    if (refElement == null) {
      DaemonCodeAnalyzer.getInstance(manager.getProject()).updateVisibleHighlighters(editor);
      return null;
    }
    else {
      if ((flags & NEW_AS_CONSTRUCTOR) != 0) {
        PsiElement parent = referenceElement.getParent();
        if (parent instanceof PsiAnonymousClass) {
          parent = parent.getParent();
        }
        if (parent instanceof PsiNewExpression) {
          PsiMethod constructor = ((PsiNewExpression)parent).resolveConstructor();
          if (constructor != null) {
            refElement = constructor;
          }
        }
      }
      if (refElement instanceof PsiClass && refElement.getContainingFile().getVirtualFile() == null) { // in mirror file of compiled class
        String qualifiedName = ((PsiClass)refElement).getQualifiedName();
        if (qualifiedName == null) return null;
        return JavaPsiFacade.getInstance(manager.getProject()).findClass(qualifiedName, refElement.getResolveScope());
      }
      return refElement;
    }
  }


  protected PsiElement getNamedElement(final PsiElement element) {
    PsiElement parent = element.getParent();
    if (element instanceof PsiIdentifier) {
      if (parent instanceof PsiClass && element.equals(((PsiClass)parent).getNameIdentifier())) {
        return parent;
      }
      else if (parent instanceof PsiVariable && element.equals(((PsiVariable)parent).getNameIdentifier())) {
        return parent;
      }
      else if (parent instanceof PsiMethod && element.equals(((PsiMethod)parent).getNameIdentifier())) {
        return parent;
      }
      else if (parent instanceof PsiLabeledStatement && element.equals(((PsiLabeledStatement)parent).getLabelIdentifier())) {
        return parent;
      }
    }
    else if ((parent = PsiTreeUtil.getParentOfType(element, PsiNamedElement.class, false)) != null) {
      // A bit hacky depends on navigation offset correctly overridden
      if (parent.getTextOffset() == element.getTextRange().getStartOffset() && !(parent instanceof XmlAttribute)) {
        return parent;
      }
    }
    return null;
  }

  @Nullable
  public static PsiReferenceExpression findReferenceExpression(Editor editor) {
    final PsiReference ref = findReference(editor);
    return ref instanceof PsiReferenceExpression ? (PsiReferenceExpression)ref : null;
  }

  @Nullable
  @Override
  public PsiElement adjustElement(final Editor editor, final int flags, final PsiElement element, @NotNull final PsiElement contextElement) {
    if (element != null) {
      if (element instanceof PsiAnonymousClass) {
        return ((PsiAnonymousClass)element).getBaseClassType().resolve();
      }
      return element;
    }
    final PsiElement parent = contextElement.getParent();
    if (parent instanceof XmlText || parent instanceof XmlAttributeValue) {
      return TargetElementUtilBase.getInstance().findTargetElement(editor, flags, parent.getParent().getTextRange().getStartOffset() + 1);
    }
    else if (parent instanceof XmlTag || parent instanceof XmlAttribute) {
      return TargetElementUtilBase.getInstance().findTargetElement(editor, flags, parent.getTextRange().getStartOffset() + 1);
    }
    return null;
  }
}
