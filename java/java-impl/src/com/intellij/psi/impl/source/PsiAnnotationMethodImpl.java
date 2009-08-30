package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.util.PatchedSoftReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class PsiAnnotationMethodImpl extends PsiMethodImpl implements PsiAnnotationMethod {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiAnnotationMethodImpl");
  private PatchedSoftReference<PsiAnnotationMemberValue> myCachedDefaultValue = null;

  public PsiAnnotationMethodImpl(final PsiMethodStub stub) {
    super(stub);
  }

  public PsiAnnotationMethodImpl(final ASTNode node) {
    super(node);
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return PsiModifier.ABSTRACT.equals(name) || PsiModifier.PUBLIC.equals(name) || super.hasModifierProperty(name);
  }

  protected void dropCached() {
    myCachedDefaultValue = null;
  }

  public PsiAnnotationMemberValue getDefaultValue() {
    final PsiMethodStub stub = getStub();
    if (stub != null) {
      final String text = stub.getDefaultValueText();
      if (StringUtil.isEmpty(text)) return null;

      if (myCachedDefaultValue != null) {
        final PsiAnnotationMemberValue value = myCachedDefaultValue.get();
        if (value != null) {
          return value;
        }
      }

      @NonNls final String annoText = "@interface _Dummy_ { Class foo() default " + text + "; }";
      final PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(getProject()).createFileFromText("a.java", annoText);
      final PsiAnnotationMemberValue value = ((PsiAnnotationMethod)file.getClasses()[0].getMethods()[0]).getDefaultValue();
      myCachedDefaultValue = new PatchedSoftReference<PsiAnnotationMemberValue>(value);
      return value;
    }

    myCachedDefaultValue = null;

    final ASTNode node = getNode().findChildByRole(ChildRole.ANNOTATION_DEFAULT_VALUE);
    if (node == null) return null;
    return (PsiAnnotationMemberValue)node.getPsi();
  }

  @NonNls
  public String toString() {
    return "PsiAnnotationMethod:" + getName();
  }

  public final void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotationMethod(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
}