// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.UntaintedAnnotationProvider;
import com.intellij.lang.jvm.JvmModifiersOwner;
import com.intellij.lang.jvm.actions.AnnotationRequest;
import com.intellij.lang.jvm.actions.AnnotationRequestsKt;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UastContextKt;

import java.util.List;

public class MarkAsSafeFix extends LocalQuickFixOnPsiElement {

  private final String myName;

  protected MarkAsSafeFix(@NotNull PsiElement element, @NotNull String name) {
    super(element);
    this.myName = name;
  }

  @Override
  public @NotNull String getText() {
    return JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.mark.as.safe.text", myName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.mark.as.safe.family");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    UExpression uExpression = UastContextKt.toUElementOfExpectedTypes(startElement, UCallExpression.class, UReferenceExpression.class);
    if (uExpression == null) return;
    TaintAnalyzer taintAnalyzer = new TaintAnalyzer();
    TaintValue taintValue = taintAnalyzer.analyze(uExpression);
    if (taintValue != TaintValue.UNKNOWN) return;
    taintAnalyzer.getNonMarkedElements().forEach(owner -> markAsSafe(project, owner.myNonMarked));
  }

  public static void markAsSafe(@NotNull Project project, @NotNull PsiModifierListOwner owner) {
    AnnotationRequest request = AnnotationRequestsKt.annotationRequest(UntaintedAnnotationProvider.DEFAULT_UNTAINTED_ANNOTATION);
    PsiFile file = owner.getContainingFile();
    VirtualFile virtualFile = file.getVirtualFile();
    TextEditor textEditor = ObjectUtils.tryCast(FileEditorManager.getInstance(project).getSelectedEditor(virtualFile), TextEditor.class);
    if (textEditor == null) return;
    Editor editor = textEditor.getEditor();
    // TODO: support for kotlin type use annotations
    List<IntentionAction> actions = JvmElementActionFactories.createAddAnnotationActions((JvmModifiersOwner)owner, request);
    if (actions.size() == 1) actions.get(0).invoke(project, editor, file);
    CodeStyleManager.getInstance(project).reformat(owner);
  }
}
