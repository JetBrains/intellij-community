// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.PsiClassRenderingInfo;
import com.intellij.ide.util.PsiElementRenderingInfo;
import com.intellij.ide.util.PsiMethodRenderingInfo;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastContextKt;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class TaintNode extends PresentableNodeDescriptor<TaintNode> {

  private final @Nullable SmartPsiElementPointer<PsiElement> myPsiElement;

  private final @Nullable SmartPsiElementPointer<PsiElement> myRef;
  List<TaintNode> myCachedChildren;
  TaintValue myTaintValue = TaintValue.UNKNOWN;
  boolean isTaintFlowRoot;
  private boolean isExcluded;

  private final @Nullable Icon myIcon;
  private final @Nullable TaintValueFactory myTaintValueFactory;

  private final boolean myNext;

  private final @NotNull PresentationData data  = new PresentationData();

  TaintNode(@Nullable TaintNode parent,
            @Nullable PsiElement psiElement,
            @Nullable PsiElement ref,
            @Nullable TaintValueFactory taintValueFactory,
            boolean next) {
    super(parent == null ? null : parent.myProject, parent);
    myPsiElement = psiElement == null ? null : SmartPointerManager.createPointer(psiElement);
    myRef = ref == null ? null : SmartPointerManager.createPointer(ref);
    myTaintValueFactory = taintValueFactory;
    int flags = Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS;
    myIcon = psiElement == null ? null : psiElement.getIcon(flags);
    myNext = next;
    appendPsiElement(psiElement);
  }

  @Override
  public TaintNode getElement() {
    return this;
  }

  public PsiElement getRef() {
    return myRef == null ? null : myRef.getElement();
  }

  public @Nullable PsiElement getPsiElement() {
    return myPsiElement == null ? null : myPsiElement.getElement();
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
  }

  public List<TaintNode> calcChildren() {
    PsiElement psiElement = getPsiElement();
    if (psiElement == null) return null;
    if (myCachedChildren != null) return myCachedChildren;
    PsiElement elementRef = myRef == null ? null : myRef.getElement();
    if (elementRef == null) {
      return null;
    }
    myCachedChildren = propagate(psiElement, elementRef);
    if (isExcluded) myCachedChildren.forEach(c -> c.setExcluded(isExcluded));
    return myCachedChildren;
  }

  private @NotNull List<TaintNode> propagate(@NotNull PsiElement psiElement, @NotNull PsiElement elementRef) {
    if (myTaintValueFactory == null) return List.of();
    if (!myNext) return List.of();
    TaintAnalyzer taintAnalyzer = new TaintAnalyzer(myTaintValueFactory);
    UExpression uExpression = UastContextKt.toUElementOfExpectedTypes(elementRef, UExpression.class);
    if (uExpression == null) return Collections.emptyList();
    TaintValue taintValue;
    try {
      taintValue = taintAnalyzer.analyzeExpression(uExpression, true, TaintValue.TAINTED);
    }
    catch (DeepTaintAnalyzerException e) {
      return Collections.emptyList();
    }
    myTaintValue = taintValue;
    if (taintValue == TaintValue.UNTAINTED) return Collections.emptyList();
    if (taintValue == TaintValue.TAINTED) {
      markTainted();
      return Collections.emptyList();
    }
    Set<PsiElement> parents = collectParents();
    parents.add(psiElement);
    List<TaintNode> children = new ArrayList<>();
    for (NonMarkedElement nonMarkedElement : taintAnalyzer.getNonMarkedElements()) {
      if (parents.contains(nonMarkedElement.myNonMarked)) continue;
      TaintNode child =
        new TaintNode(this, nonMarkedElement.myNonMarked, nonMarkedElement.myRef, myTaintValueFactory, nonMarkedElement.myNext);
      children.add(child);
    }
    return children;
  }

  @Override
  protected @NotNull PresentationData createPresentation() {
    PresentationData data = new PresentationData();
    PsiElement psiElement = this.getPsiElement();
    if (psiElement == null) {
      append(data, UsageViewBundle.message("node.invalid"), SimpleTextAttributes.ERROR_ATTRIBUTES);
      return data;
    }
    data.setIcon(myIcon);
    data.applyFrom(this.data);
    if (!this.isTaintFlowRoot) return data;
    String unsafeFlow = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.toolwindow.unsafe.flow");
    SimpleTextAttributes attributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, UIUtil.getLabelInfoForeground());
    append(data, unsafeFlow, attributes);
    return data;
  }

  private static void append(@NotNull PresentationData data,
                             @NlsContexts.Label @NotNull String message, @NotNull SimpleTextAttributes attributes) {
    data.addText(message, attributes);
  }

  private void appendPsiElement(@Nullable PsiElement psiElement) {
    if (psiElement == null) {
      return;
    }
    TaintNode taintNode = this;
    int style = taintNode.isExcluded() ? SimpleTextAttributes.STYLE_STRIKEOUT : SimpleTextAttributes.STYLE_PLAIN;
    Color color = taintNode.myTaintValue == TaintValue.TAINTED ? NamedColorUtil.getErrorForeground() : null;
    SimpleTextAttributes attributes = new SimpleTextAttributes(style, color);
    PsiMethod psiMethod = ObjectUtils.tryCast(psiElement, PsiMethod.class);
    if (psiMethod != null) {
      PsiMethodRenderingInfo renderingInfo = new PsiMethodRenderingInfo(true);
      String text = renderingInfo.getPresentableText(psiMethod);
      append(data, text, attributes);
      return;
    }
    PsiVariable psiVariable = ObjectUtils.tryCast(psiElement, PsiVariable.class);
    if (psiVariable != null) {
      String varText =
        PsiFormatUtil.formatVariable(psiVariable, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE, PsiSubstitutor.EMPTY);
      append(data, varText, attributes);
      PsiNameIdentifierOwner parent = PsiTreeUtil.getParentOfType(psiVariable, PsiClass.class, PsiMethod.class);
      Color placeColor = attributes.getFgColor();
      if (placeColor == null) placeColor = UIUtil.getLabelInfoForeground();
      SimpleTextAttributes placeAttribute = new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, placeColor);
      if (parent instanceof PsiMethod) {
        PsiMethodRenderingInfo renderingInfo = new PsiMethodRenderingInfo(true);
        append(data, ": " + renderingInfo.getPresentableText((PsiMethod)parent), placeAttribute);
      }
      else if (parent instanceof PsiClass) {
        PsiElementRenderingInfo<PsiClass> renderingInfo = PsiClassRenderingInfo.INSTANCE;
        append(data, ": " + renderingInfo.getPresentableText((PsiClass)parent), placeAttribute);
      }
      return;
    }
    PsiNamedElement namedElement = ObjectUtils.tryCast(psiElement, PsiNamedElement.class);
    if (namedElement == null) return;
    String name = namedElement.getName();
    if (name == null) return;
    append(data, name, attributes);
  }

  private void markTainted() {
    myTaintValue = TaintValue.TAINTED;
    TaintNode parent = ObjectUtils.tryCast(getParentDescriptor(), TaintNode.class);
    if (parent != null) {
      List<TaintNode> siblings = parent.myCachedChildren;
      if (siblings != null && siblings.size() == 1) {
        parent.markTainted();
        return;
      }
    }
    isTaintFlowRoot = true;
  }

  private @NotNull Set<PsiElement> collectParents() {
    Set<PsiElement> parents = new HashSet<>();
    TaintNode parent = ObjectUtils.tryCast(getParentDescriptor(), TaintNode.class);
    while (parent != null) {
      PsiElement parentPsiElement = parent.getPsiElement();
      if (parentPsiElement == null || parent.getParentDescriptor() == null) return parents;
      parents.add(parentPsiElement);
      parent = ObjectUtils.tryCast(parent.getParentDescriptor(), TaintNode.class);
    }
    return parents;
  }

  boolean isExcluded() {
    return isExcluded;
  }

  void setExcluded(boolean excluded) {
    isExcluded = excluded;
  }
}
