// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl.fixes;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.CompleteMacro;
import com.intellij.codeInspection.*;
import com.intellij.json.psi.JsonElementGenerator;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.impl.JsonValidationError;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AddMissingPropertyFix implements LocalQuickFix, BatchQuickFix<CommonProblemDescriptor> {
  private final JsonValidationError.MissingPropertyIssueData myData;

  public AddMissingPropertyFix(JsonValidationError.MissingPropertyIssueData data) {
    myData = data;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return "Add missing property";
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getName() {
    return getFamilyName() + " '" + myData.propertyName + "'";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    Ref<Boolean> hadComma = Ref.create(false);
    if (!(element instanceof JsonObject)) return;
    PsiElement newElement = performFix(project, element, hadComma);
    JsonValue value = ((JsonProperty)newElement).getValue();
    FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(element.getContainingFile().getVirtualFile());
    EditorEx editor = EditorUtil.getEditorEx(fileEditor);
    assert editor != null;
    if (value == null) {
      WriteAction.run(() ->editor.getCaretModel().moveToOffset(newElement.getTextRange().getEndOffset()));
      return;
    }
    TemplateManager templateManager = TemplateManager.getInstance(project);
    TemplateBuilderImpl builder = new TemplateBuilderImpl(newElement);
    builder.replaceElement(value, myData.hasEnumItems
                                    ? new MacroCallNode(new CompleteMacro())
                                    : new ConstantNode(value.getText()));
    Template template = builder.buildTemplate();
    int offset = newElement.getTextRange().getEndOffset();
    // yes, we need separate write actions for each step
    WriteAction.run(() -> editor.getCaretModel().moveToOffset(hadComma.get() ? offset + 1 : offset));
    WriteAction.run(() -> newElement.delete());
    WriteAction.run(() -> editor.getDocument().insertString(editor.getCaretModel().getOffset(), "\n"));
    template.setToReformat(true);
    templateManager.startTemplate(editor, template);
  }

  private PsiElement performFix(@NotNull Project project, PsiElement element, Ref<Boolean> hadComma) {
    JsonElementGenerator generator = new JsonElementGenerator(project);
    Object defaultValueObject = myData.defaultValue;
    String defaultValue = defaultValueObject instanceof String ? StringUtil.wrapWithDoubleQuote(defaultValueObject.toString()) : null;
    Ref<PsiElement> newElementRef = Ref.create(null);

    WriteAction.run(() -> {
      PsiElement newElement = element
                        .addBefore(
                          generator.createProperty(myData.propertyName, defaultValue == null ? myData.propertyType.getDefaultValue() : defaultValue),
                          element.getLastChild());
                      PsiElement backward = PsiTreeUtil.skipWhitespacesBackward(newElement);
                      if (backward instanceof JsonProperty) {
                        element.addAfter(generator.createComma(), backward);
                        hadComma.set(true);
                      }
                      newElementRef.set(newElement);
                    });

    return newElementRef.get();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project,
                       @NotNull CommonProblemDescriptor[] descriptors,
                       @NotNull List<PsiElement> psiElementsToIgnore,
                       @Nullable Runnable refreshViews) {
    List<Pair<AddMissingPropertyFix, PsiElement>> propFixes = ContainerUtil.newArrayList();
    for (CommonProblemDescriptor descriptor: descriptors) {
      if (!(descriptor instanceof ProblemDescriptor)) continue;
      QuickFix[] fixes = descriptor.getFixes();
      if (fixes == null) continue;
      AddMissingPropertyFix fix = getWorkingQuickFix(fixes);
      if (fix == null) continue;
      propFixes.add(Pair.create(fix, ((ProblemDescriptor)descriptor).getPsiElement()));
    }

    DocumentUtil.writeInRunUndoTransparentAction(() -> propFixes.forEach(fix ->
                                                   fix.first.performFix(project, fix.second, Ref.create(false))));
  }

  @Nullable
  private static AddMissingPropertyFix getWorkingQuickFix(@NotNull QuickFix[] fixes) {
    for (QuickFix fix : fixes) {
      if (fix instanceof AddMissingPropertyFix) {
        return (AddMissingPropertyFix)fix;
      }
    }
    return null;
  }
}
