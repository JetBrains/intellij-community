// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.PsiElementRenderingInfo;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringFactory;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.ListSelectionModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CreateParameterFromUsageFix extends CreateFromUsageBaseFix {
  private static final Logger LOG = Logger.getInstance(CreateParameterFromUsageFix.class);
  protected final SmartPsiElementPointer<PsiReferenceExpression> myReferenceExpression;

  public CreateParameterFromUsageFix(PsiReferenceExpression referenceElement) {
    myReferenceExpression = SmartPointerManager.createPointer(referenceElement);
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    PsiReferenceExpression element = myReferenceExpression.getElement();
    if (element == null) return false;
    String varName = element.getReferenceName();
    String message = CommonQuickFixBundle.message("fix.create.title.x", JavaElementKind.PARAMETER.object(), varName);
    setText(message);
    if (element.isQualified()) return false;
    PsiElement scope = element;
    do {
      scope = PsiTreeUtil.getParentOfType(scope, PsiMethod.class, PsiClass.class);
      if (!(scope instanceof PsiAnonymousClass)) {
        return scope instanceof PsiMethod method && method.getParameterList().isPhysical();
      }
    }
    while (true);
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return currentFile;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
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
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) {
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

  @Override
  protected boolean isValidElement(PsiElement element) {
    PsiReferenceExpression expression = (PsiReferenceExpression)element;
    return CreateFromUsageUtils.isValidReference(expression, false);
  }

  @Override
  protected PsiElement getElement() {
    PsiReferenceExpression element = myReferenceExpression.getElement();
    if (element == null) return null;
    if (!element.isValid() || !canModify(element)) return null;

    PsiElement parent = element.getParent();

    if (parent instanceof PsiMethodCallExpression) return null;

    if (element.getReferenceNameElement() != null) {
      if (!CreateFromUsageUtils.isValidReference(element, false)) {
        return element;
      }
    }

    return null;
  }

  private static void chooseEnclosingMethod(Editor editor, PsiMethod method, Consumer<PsiMethod> consumer) {
    final List<PsiMethod> validEnclosingMethods = CommonJavaRefactoringUtil.getEnclosingMethods(method);
    if (validEnclosingMethods.size() > 1 && !ApplicationManager.getApplication().isUnitTestMode()) {
      PsiTargetNavigator<PsiMethod> navigator = createPsiTargetNavigator(validEnclosingMethods);
      ScopeHighlighter highlighter = new ScopeHighlighter(editor);
      navigator.builderConsumer(builder -> builder
        .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        .setItemSelectedCallback(i -> {
          highlighter.dropHighlight();
          if (i == null || !(i.dereference() instanceof PsiMethod selectedMethod)) return;
          PsiIdentifier identifier = selectedMethod.getNameIdentifier();
          if (identifier == null) return;
          highlighter.highlight(identifier, Collections.singletonList(identifier));
        })
        .addListener(new JBPopupListener() {
          @Override
          public void onClosed(@NotNull LightweightWindowEvent event) {
            highlighter.dropHighlight();
          }
        }));
      navigator.navigate(editor, QuickFixBundle.message("target.method.chooser.title"), selectedMethod -> {
        consumer.consume(selectedMethod);
        return false;
      });
    }
    else {
      consumer.consume(validEnclosingMethods.getFirst());
    }
  }

  private static @NotNull PsiTargetNavigator<PsiMethod> createPsiTargetNavigator(List<PsiMethod> validEnclosingMethods) {
    PsiElementRenderingInfo<PsiMethod> methodRenderingInfo = m -> PsiFormatUtil.formatMethod(
      m,
      PsiSubstitutor.EMPTY,
      PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
      PsiFormatUtilBase.SHOW_NAME |
      PsiFormatUtilBase.SHOW_PARAMETERS,
      PsiFormatUtilBase.SHOW_TYPE
    );
    return new PsiTargetNavigator<>(validEnclosingMethods)
      .presentationProvider(
        enclosingMethod -> PsiElementRenderingInfo.targetPresentation(enclosingMethod, methodRenderingInfo)
      );
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
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.parameter.from.usage.family");
  }
}
