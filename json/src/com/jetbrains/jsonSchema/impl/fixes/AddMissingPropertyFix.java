// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl.fixes;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.CompleteMacro;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.jsonSchema.impl.JsonValidationError;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class AddMissingPropertyFix implements LocalQuickFix {
  private final SmartPsiElementPointer<PsiElement> myPointer;
  private final JsonValidationError.MissingPropertyIssueData myData;

  public AddMissingPropertyFix(PsiElement node, JsonValidationError.MissingPropertyIssueData data) {
    myPointer = SmartPointerManager.createPointer(node);
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
    PsiElement element = myPointer.getElement();
    if (!(element instanceof JsonObject)) return;

    JsonElementGenerator generator = new JsonElementGenerator(project);
    Object defaultValueObject = myData.defaultValue;
    String defaultValue = defaultValueObject instanceof String ? StringUtil.wrapWithDoubleQuote(defaultValueObject.toString()) : null;
    Ref<PsiElement> newElementRef = Ref.create(null);
    Ref<Boolean> hadComma = Ref.create(false);

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

    PsiElement newElement = newElementRef.get();
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

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
