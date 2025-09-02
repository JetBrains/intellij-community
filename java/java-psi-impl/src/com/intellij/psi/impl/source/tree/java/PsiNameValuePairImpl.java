// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiNameValuePairStub;
import com.intellij.psi.impl.source.JavaStubPsiElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Objects;

import static com.intellij.reference.SoftReference.dereference;

/**
 * @author Dmitry Avdeev
 */
public class PsiNameValuePairImpl extends JavaStubPsiElement<PsiNameValuePairStub> implements PsiNameValuePair {

  public PsiNameValuePairImpl(@NotNull PsiNameValuePairStub stub) {
    super(stub, JavaStubElementTypes.NAME_VALUE_PAIR);
  }

  public PsiNameValuePairImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public @NotNull NameValuePairElement getNode() {
    ASTNode node = super.getNode();
    if (!(node instanceof NameValuePairElement)) {
      String parents = String.join("; ", SyntaxTraverser.psiApi().parents(this).takeWhile(Objects::nonNull)
        .map(psi -> psi.getClass().getName()).toList());
      throw new IllegalStateException("Node is not NameValuePairElement; node class = " + node.getClass() + "; parents = " + parents);
    }
    return (NameValuePairElement)node;
  }

  @Override
  public String getName() {
    PsiNameValuePairStub stub = getStub();
    if (stub == null) {
      PsiIdentifier nameIdentifier = getNameIdentifier();
      return nameIdentifier == null ? null : nameIdentifier.getText();
    }
    else {
      return stub.getName();
    }
  }

  @Override
  public String getLiteralValue() {
    PsiAnnotationMemberValue value = getValue();
    return value instanceof PsiLiteralExpression ? StringUtil.unquoteString(value.getText()) : null;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    ASTNode node = getNode().findChildByRole(ChildRole.NAME);
    return node == null ? null : (PsiIdentifier)node.getPsi();
  }

  @Override
  public PsiAnnotationMemberValue getValue() {
    PsiLiteralExpression child = getStubOrPsiChild(JavaStubElementTypes.LITERAL_EXPRESSION, PsiLiteralExpression.class);
    if (child != null) return child;

    ASTNode node = getNode().findChildByRole(ChildRole.ANNOTATION_VALUE);
    return node == null ? null : (PsiAnnotationMemberValue)node.getPsi();
  }

  @Override
  public @NotNull PsiAnnotationMemberValue setValue(@NotNull PsiAnnotationMemberValue newValue) {
    getValue().replace(newValue);
    return getValue();
  }

  private volatile Reference<PsiAnnotationMemberValue> myDetachedValue;

  @Override
  public @Nullable PsiAnnotationMemberValue getDetachedValue() {
    PsiNameValuePairStub stub = getStub();
    if (stub != null) {
      String text = stub.getValue();
      PsiAnnotationMemberValue result = dereference(myDetachedValue);
      if (result == null) {
        PsiAnnotation anno = JavaPsiFacade.getElementFactory(getProject()).createAnnotationFromText("@F(" + text + ")", this);
        ((LightVirtualFile)anno.getContainingFile().getViewProvider().getVirtualFile()).setWritable(false);
        myDetachedValue = new SoftReference<>(result = anno.findAttributeValue(null));
      }
      return result;
    }

    return getValue();
  }

  @Override
  public void subtreeChanged() {
    myDetachedValue = null;
    super.subtreeChanged();
  }

  @Override
  public PsiReference getReference() {
    return new PsiReference() {
      private @Nullable PsiClass getReferencedClass() {
        LOG.assertTrue(getParent() instanceof PsiAnnotationParameterList && getParent().getParent() instanceof PsiAnnotation);
        PsiAnnotation annotation = (PsiAnnotation)getParent().getParent();
        PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
        if (nameRef == null) return null;
        PsiElement target = nameRef.resolve();
        return target instanceof PsiClass ? (PsiClass)target : null;
      }

      @Override
      public @NotNull PsiElement getElement() {
        return PsiNameValuePairImpl.this;
      }

      @Override
      public @NotNull TextRange getRangeInElement() {
        PsiIdentifier id = getNameIdentifier();
        if (id != null) {
          return new TextRange(0, id.getTextLength());
        }
        return TextRange.EMPTY_RANGE;
      }

      @Override
      public PsiElement resolve() {
        PsiClass refClass = getReferencedClass();
        if (refClass == null) return null;
        String name = getName();
        if (name == null) name = PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME;
        MethodSignature signature = MethodSignatureUtil
          .createMethodSignature(name, PsiType.EMPTY_ARRAY, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
        return MethodSignatureUtil.findMethodBySignature(refClass, signature, false);
      }

      @Override
      public @NotNull String getCanonicalText() {
        String name = getName();
        return name != null ? name : PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME;
      }

      @Override
      public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        PsiIdentifier nameIdentifier = getNameIdentifier();
        if (nameIdentifier != null) {
          PsiImplUtil.setName(nameIdentifier, newElementName);
        }
        else if (ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET.contains(getNode().getFirstChildNode().getElementType())) {
          PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
          nameIdentifier = factory.createIdentifier(newElementName);
          addBefore(nameIdentifier, SourceTreeToPsiMap.treeElementToPsi(getNode().getFirstChildNode()));
        }

        return PsiNameValuePairImpl.this;
      }

      @Override
      public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        throw new IncorrectOperationException("Not implemented");
      }

      @Override
      public boolean isReferenceTo(@NotNull PsiElement element) {
        return element instanceof PsiMethod && element.equals(resolve());
      }

      @Override
      public boolean isSoft() {
        return false;
      }
    };
  }

  @Override
  public final void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitNameValuePair(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiNameValuePair";
  }

  private static final Logger LOG = Logger.getInstance(PsiNameValuePairImpl.class);

}
