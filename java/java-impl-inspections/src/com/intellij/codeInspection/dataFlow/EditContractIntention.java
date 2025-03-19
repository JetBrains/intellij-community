// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.DefaultInferredAnnotationProvider;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.ExternalAnnotationsManagerImpl;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.codeInspection.options.StringValidator;
import com.intellij.codeInspection.ui.OptPaneUtils;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

import static com.intellij.codeInspection.options.OptPane.*;

public final class EditContractIntention extends BaseIntentionAction implements LowPriorityAction {
  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.edit.method.contract");
  }

  private static @Nullable PsiMethod getTargetMethod(Editor editor, PsiFile file) {
    final PsiModifierListOwner owner =  AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    if (owner instanceof PsiMethod && ExternalAnnotationsManagerImpl.areExternalAnnotationsApplicable(owner)) {
      PsiElement original = owner.getOriginalElement();
      return original instanceof PsiMethod ? (PsiMethod)original : (PsiMethod)owner;
    }
    return null;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiMethod method = getTargetMethod(editor, file);
    PsiAnnotation annotation = method == null ? null : JavaMethodContractUtil.findContractAnnotation(method);
    String text = "(\"...\")";
    if (annotation != null) {
      text = annotation.getParameterList().getText();
    }
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, "@Contract()\nclass X{}",
                                               "@Contract" + text + "\nclass X{}");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiMethod method = getTargetMethod(editor, file);
    if (method != null) {
      boolean hasContract = JavaMethodContractUtil.findContractAnnotation(method) != null;
      setText(hasContract ? JavaBundle.message("intention.text.edit.method.contract.of.0", method.getName())
                          : JavaBundle.message("intention.text.add.method.contract.to.0", method.getName()));
      return true;
    }
    return false;
  }

  private static class ContractData implements OptionContainer {
    @NlsSafe String contract = "";
    boolean impure = true;
    @NlsSafe String mutates = "";

    private static @NotNull ContractData fromAnnotation(@Nullable PsiAnnotation existingAnno) {
      ContractData data = new ContractData();
      if (existingAnno != null) {
        data.contract = AnnotationUtil.getStringAttributeValue(existingAnno, "value");
        data.impure = !Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(existingAnno, "pure"));
        data.mutates = AnnotationUtil.getStringAttributeValue(existingAnno, "mutates");
      }
      return data;
    }

    public OptPane getOptionPane(@NotNull PsiMethod method) {
      return pane(
        string("contract", JavaBundle.message("label.contract"),
               StringValidator.of("java.method.contract", string -> getContractErrorMessage(string, method)))
          .description(HtmlChunk.raw(JavaBundle.message("edit.contract.dialog.hint"))),
        checkbox("impure", JavaBundle.message("edit.contract.dialog.checkbox.impure.method"),
                 string("mutates", JavaBundle.message("label.mutates"),
                        StringValidator.of("java.method.mutates", string -> getMutatesErrorMessage(string, method)))
                   .description(HtmlChunk.raw(JavaBundle.message("edit.contract.dialog.mutates.hint")))
        ));
    }
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiMethod method = getTargetMethod(editor, file);
    assert method != null;
    PsiAnnotation existingAnno = AnnotationUtil.findAnnotationInHierarchy(method, Collections.singleton(Contract.class.getName()));
    ContractData data = ContractData.fromAnnotation(existingAnno);
    OptPaneUtils.editOptions(project, data, data.getOptionPane(method), JavaBundle.message("dialog.title.edit.method.contract"),
                             "define_contract_dialog", () -> updateContract(method, data));
  }

  private static void updateContract(@NotNull PsiMethod method, @NotNull ContractData data) {
    Project project = method.getProject();
    ExternalAnnotationsManager manager = ExternalAnnotationsManager.getInstance(project);
    manager.deannotate(method, JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT);
    PsiAnnotation mockAnno = DefaultInferredAnnotationProvider.createContractAnnotation(project, !data.impure, data.contract, data.mutates);
    if (mockAnno != null) {
      try {
        manager.annotateExternally(method, JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT, method.getContainingFile(),
                                   mockAnno.getParameterList().getAttributes());
      }
      catch (ExternalAnnotationsManager.CanceledConfigurationException ignored) {}
    }
    DaemonCodeAnalyzerEx.getInstanceEx(project).restart("EditContractIntention.updateContract");
  }

  private static @Nullable @NlsContexts.DialogMessage String getMutatesErrorMessage(String mutates, PsiMethod method) {
    return StringUtil.isEmpty(mutates) ? null : MutationSignature.checkSignature(mutates, method);
  }

  private static @Nullable @NlsContexts.DialogMessage String getContractErrorMessage(String contract, PsiMethod method) {
    if (StringUtil.isEmpty(contract)) {
      return null;
    }
    StandardMethodContract.ParseException error = ContractInspection.checkContract(method, contract);
    return error != null ? error.getMessage() : null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
