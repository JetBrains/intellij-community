// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.refactoring.JavaRefactoringFactory;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CreateParameterFromUsageFix extends CreateVarFromUsageFix {
  private static final Logger LOG = Logger.getInstance(CreateParameterFromUsageFix.class);

  public CreateParameterFromUsageFix(PsiReferenceExpression referenceElement) {
    super(referenceElement);
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    if (!super.isAvailableImpl(offset)) return false;
    PsiReferenceExpression element = myReferenceExpression.getElement();
    if (element == null) return false;
    if (element.isQualified()) return false;
    PsiElement scope = element;
    do {
      scope = PsiTreeUtil.getParentOfType(scope, PsiMethod.class, PsiClass.class);
      if (!(scope instanceof PsiAnonymousClass)) {
        return scope instanceof PsiMethod &&
               ((PsiMethod)scope).getParameterList().isPhysical();
      }
    }
    while (true);
  }

  @Override
  public String getText(String varName) {
    return CommonQuickFixBundle.message("fix.create.title.x", JavaElementKind.PARAMETER.object(), varName);
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return currentFile;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiReferenceExpression element = myReferenceExpression.getElement();
    if (element == null) return IntentionPreviewInfo.EMPTY;
    PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (method == null) return IntentionPreviewInfo.EMPTY;
    List<ParameterInfoImpl> infos = getParameterInfos(method);
    String newParameters = "(" + StringUtil.join(infos, i -> i.getTypeText() + " " + i.getName(), ", ") + ")";
    StringBuilder newText = new StringBuilder();
    for (PsiElement child : method.getChildren()) {
      newText.append(child instanceof PsiParameterList ? newParameters : child.getText());
    }
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, method.getText(), newText.toString());
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    PsiReferenceExpression element = myReferenceExpression.getElement();
    if(element==null) return;
    if (CreateFromUsageUtils.isValidReference(element, false)) return;

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

    PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    LOG.assertTrue(method != null);
    chooseEnclosingMethod(editor, method, m -> {
      m = SuperMethodWarningUtil.checkSuperMethod(m);
      if (m == null) return;

      final List<ParameterInfoImpl> parameterInfos = getParameterInfos(m);
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        ParameterInfoImpl[] array = parameterInfos.toArray(new ParameterInfoImpl[0]);
        String modifier = PsiUtil.getAccessModifier(PsiUtil.getAccessLevel(m.getModifierList()));
        var processor = JavaRefactoringFactory.getInstance(project)
          .createChangeSignatureProcessor(m, false, modifier, m.getName(), m.getReturnType(), array, null, null, null, null);
        processor.run();
      }
      else {
        JavaChangeSignatureDialog dialog =
          JavaChangeSignatureDialog.createAndPreselectNew(project, m, parameterInfos, true, element);
        dialog.setParameterInfos(parameterInfos);
        if (dialog.showAndGet()) {
          final String varName = element.getReferenceName();
          for (ParameterInfoImpl info : parameterInfos) {
            if (info.isNew()) {
              final String newParamName = info.getName();
              if (!Comparing.strEqual(varName, newParamName)) {
                final PsiExpression newExpr = JavaPsiFacade.getElementFactory(project).createExpressionFromText(newParamName, m);
                WriteCommandAction.writeCommandAction(project).run(() -> {
                  final PsiReferenceExpression[] refs =
                    CreateFromUsageUtils.collectExpressions(element, PsiMember.class, PsiFile.class);
                  for (PsiReferenceExpression ref : refs) {
                    ref.replace(newExpr.copy());
                  }
                });
              }
              break;
            }
          }
        }
      }
    });
  }

  private static void chooseEnclosingMethod(Editor editor, PsiMethod method, Consumer<PsiMethod> consumer) {
    final List<PsiMethod> validEnclosingMethods = CommonJavaRefactoringUtil.getEnclosingMethods(method);
    if (validEnclosingMethods.size() > 1 && !ApplicationManager.getApplication().isUnitTestMode()) {
      PsiElementListCellRenderer<PsiMethod> renderer = new PsiElementListCellRenderer<>() {
        @Override
        public String getElementText(PsiMethod method) {
          return PsiFormatUtil.formatMethod(
            method,
            PsiSubstitutor.EMPTY,
            PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
            PsiFormatUtilBase.SHOW_TYPE);
        }

        @Override
        protected @Nullable String getContainerText(PsiMethod method, String name) {
          return null;
        }
      };
      ScopeHighlighter highlighter = new ScopeHighlighter(editor);
      IPopupChooserBuilder<PsiMethod> builder = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(validEnclosingMethods)
        .setRenderer(renderer)
        .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        .setTitle(QuickFixBundle.message("target.method.chooser.title"))
        .setItemChosenCallback(consumer)
        .setItemSelectedCallback(i -> {
          highlighter.dropHighlight();
          if (i == null) return;
          PsiIdentifier identifier = i.getNameIdentifier();
          if (identifier == null) return;
          highlighter.highlight(identifier, Collections.singletonList(identifier));
        })
        .addListener(new JBPopupListener() {
          @Override
          public void onClosed(@NotNull LightweightWindowEvent event) {
            highlighter.dropHighlight();
          }
        });
      renderer.installSpeedSearch(builder);
      builder.createPopup().showInBestPositionFor(editor);
    }
    else {
      consumer.consume(validEnclosingMethods.get(0));
    }
  }

  private @NotNull List<ParameterInfoImpl> getParameterInfos(PsiMethod method) {
    PsiReferenceExpression element = myReferenceExpression.getElement();
    if (element == null) return Collections.emptyList();
    final String parameterName = element.getReferenceName();
    PsiType[] expectedTypes = CreateFromUsageUtils.guessType(element, false);
    final List<ParameterInfoImpl> parameterInfos =
      new ArrayList<>(Arrays.asList(ParameterInfoImpl.fromMethod(method)));
    ParameterInfoImpl parameterInfo =
      ParameterInfoImpl.createNew().withName(parameterName).withType(expectedTypes[0]).withDefaultValue(parameterName);
    if (!method.isVarArgs()) {
      parameterInfos.add(parameterInfo);
    }
    else {
      parameterInfos.add(parameterInfos.size() - 1, parameterInfo);
    }
    return parameterInfos;
  }

  @Override
  protected boolean isAllowOuterTargetClass() {
    return false;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.parameter.from.usage.family");
  }
}
