// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.DefaultInferredAnnotationProvider;
import com.intellij.codeInsight.ExternalAnnotationsManagerImpl;
import com.intellij.codeInsight.ModCommandAwareExternalAnnotationsManager;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.codeInspection.options.StringValidator;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.*;
import static java.util.Objects.requireNonNullElse;

public final class EditContractIntention implements ModCommandAction {
  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.edit.method.contract");
  }

  private static @Nullable PsiMethod getTargetMethod(@NotNull ActionContext context) {
    final PsiModifierListOwner owner = AddAnnotationPsiFix.getContainer(context.file(), context.offset());
    if (owner instanceof PsiMethod method && ExternalAnnotationsManagerImpl.areExternalAnnotationsApplicable(method)) {
      return owner.getOriginalElement() instanceof PsiMethod origMethod ? origMethod : method;
    }
    return null;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull ActionContext context) {
    PsiMethod method = getTargetMethod(context);
    PsiAnnotation annotation = method == null ? null : JavaMethodContractUtil.findContractAnnotation(method);
    String text = "(\"...\")";
    if (annotation != null) {
      text = annotation.getParameterList().getText();
    }
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, "@Contract()\nclass X{}",
                                               "@Contract" + text + "\nclass X{}");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    final PsiMethod method = getTargetMethod(context);
    if (method != null) {
      boolean hasContract = JavaMethodContractUtil.findContractAnnotation(method) != null;
      return Presentation.of(hasContract ? JavaBundle.message("intention.text.edit.method.contract.of.0", method.getName())
                                         : JavaBundle.message("intention.text.add.method.contract.to.0", method.getName())).withPriority(
        PriorityAction.Priority.LOW);
    }
    return null;
  }

  private static class ContractData implements OptionContainer {
    @NotNull private final PsiMethod method;
    @NlsSafe String contract = "";
    boolean impure = true;
    @NlsSafe String mutates = "";

    private ContractData(@NotNull PsiMethod method) {
      this.method = method;
    }

    private static @NotNull ContractData fromAnnotation(@NotNull PsiMethod method, @Nullable PsiAnnotation existingAnno) {
      ContractData data = new ContractData(method);
      if (existingAnno != null) {
        data.contract = AnnotationUtil.getStringAttributeValue(existingAnno, "value");
        data.impure = !Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(existingAnno, "pure"));
        data.mutates = AnnotationUtil.getStringAttributeValue(existingAnno, "mutates");
      }
      return data;
    }

    @Override
    public @NotNull OptPane getOptionsPane() {
      return pane(
        string("contract", JavaBundle.message("label.contract"),
               StringValidator.of("java.method.contract", string -> getContractErrorMessage(string, method)))
          .description(HtmlChunk.raw(JavaBundle.message("edit.contract.dialog.hint"))),
        checkbox("impure", JavaBundle.message("edit.contract.dialog.checkbox.impure.method"),
                 string("mutates", JavaBundle.message("label.mutates"),
                        StringValidator.of("java.method.mutates", string -> getMutatesErrorMessage(string, method)))
                   .description(HtmlChunk.raw(JavaBundle.message("edit.contract.dialog.mutates.hint")))
        )).withHelpId("define_contract_dialog");
    }
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    final PsiMethod method = getTargetMethod(context);
    if (method == null) return ModCommand.nop();
    PsiAnnotation existingAnno = AnnotationUtil.findAnnotationInHierarchy(method, Collections.singleton(Contract.class.getName()));
    return new ModEditOptions<>(JavaBundle.message("dialog.title.edit.method.contract"),
                                () -> ContractData.fromAnnotation(method, existingAnno),
                                false,
                                data -> updateContract(method, data));
  }

  private static @NotNull ModCommand updateContract(@NotNull PsiMethod method, @NotNull ContractData data) {
    Project project = method.getProject();
    var manager = ModCommandAwareExternalAnnotationsManager.getInstance(project);
    PsiAnnotation mockAnno = DefaultInferredAnnotationProvider.createContractAnnotation(
      project, !data.impure, data.contract, requireNonNullElse(data.mutates, ""));
    if (mockAnno != null) {
      return manager.annotateExternallyModCommand(method, JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT,
                                                  mockAnno.getParameterList().getAttributes());
    }
    return manager.deannotateModCommand(List.of(method), List.of(JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT));
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
}
