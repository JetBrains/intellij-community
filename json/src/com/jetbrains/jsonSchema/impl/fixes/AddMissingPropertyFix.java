// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.fixes;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInsight.template.impl.EmptyNode;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.CompleteMacro;
import com.intellij.codeInspection.*;
import com.intellij.json.JsonBundle;
import com.intellij.json.JsonLanguage;
import com.intellij.lang.Language;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonValidationError;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectRenderingLanguage;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaReader2.renderSchemaNode;

public final class AddMissingPropertyFix implements LocalQuickFix, BatchQuickFix {
  private final JsonValidationError.MissingMultiplePropsIssueData myData;
  private final JsonLikeSyntaxAdapter myQuickFixAdapter;

  public AddMissingPropertyFix(JsonValidationError.MissingMultiplePropsIssueData data,
                               JsonLikeSyntaxAdapter quickFixAdapter) {
    myData = data;
    myQuickFixAdapter = quickFixAdapter;
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return JsonBundle.message("add.missing.properties");
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getName() {
    return JsonBundle.message("add.missing.0", myData.getMessage(true));
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element);
    if (walker == null) return;
    VirtualFile file = element.getContainingFile().getVirtualFile();
    PsiElement newElement = performFix(element);
    // if we have more than one property, don't expand templates and don't move the caret
    if (newElement == null) return;

    Collection<JsonValueAdapter> values = Objects.requireNonNull(walker.getParentPropertyAdapter(newElement)).getValues();
    PsiElement value;
    if (values.size() == 1) value = values.iterator().next().getDelegate();
    else value = null;
    FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(file);
    Editor editor = fileEditor instanceof TextEditor ? ((TextEditor)fileEditor).getEditor() : null;
    assert editor != null;
    if (value == null || value.getText().isBlank()) {
      WriteAction.run(() -> editor.getCaretModel().moveToOffset(newElement.getTextRange().getEndOffset()));
      return;
    }
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    TemplateManager templateManager = TemplateManager.getInstance(project);
    TemplateBuilderImpl builder = new TemplateBuilderImpl(newElement);
    String text = value.getText();
    boolean isEmptyArray = StringUtil.equalsIgnoreWhitespaces(text, "[]");
    boolean isEmptyObject = StringUtil.equalsIgnoreWhitespaces(text, "{}");
    boolean goInside = isEmptyArray || isEmptyObject || StringUtil.isQuotedString(text);
    TextRange range = goInside ? TextRange.create(1, text.length() - 1) : TextRange.create(0, text.length());
    builder.replaceElement(value, range, myData.myMissingPropertyIssues.iterator().next().enumItemsCount > 1 || isEmptyObject
                                          ? new MacroCallNode(new CompleteMacro())
                                          : isEmptyArray ? new EmptyNode() : new ConstantNode(goInside ? StringUtil.unquoteString(text) : text));
    editor.getCaretModel().moveToOffset(newElement.getTextRange().getStartOffset());
    if (PsiTreeUtil.nextLeaf(newElement) != null) {
      builder.setEndVariableAfter(newElement);
    }
    else {
      builder.setEndVariableBefore(newElement.getLastChild());
    }
    WriteAction.run(() -> {
            Template template = builder.buildInlineTemplate();
            template.setToReformat(true);
            templateManager.startTemplate(editor, template);
          });
  }

  public @Nullable PsiElement performFix(@Nullable PsiElement node) {
    if (node == null) return null;
    PsiElement element = node instanceof PsiFile ? node.getFirstChild() : node;
    Ref<PsiElement> newElementRef = Ref.create(null);
    WriteAction.run(() -> { performFixInner(element, newElementRef); });
    return newElementRef.get();
  }

  public void performFixInner(PsiElement element, Ref<PsiElement> newElementRef) {
    boolean isSingle = myData.myMissingPropertyIssues.size() == 1;
    PsiElement processedElement = element;
    List<JsonValidationError.MissingPropertyIssueData> reverseOrder
      = ContainerUtil.reverse(new ArrayList<>(myData.myMissingPropertyIssues));
    for (JsonValidationError.MissingPropertyIssueData issue: reverseOrder) {
      Object defaultValueObject = issue.defaultValue;
      String defaultValue = formatDefaultValue(defaultValueObject, element.getLanguage());
      PsiElement property = myQuickFixAdapter.createProperty(issue.propertyName, defaultValue == null
                                                                                 ? myQuickFixAdapter
                                                                                   .getDefaultValueFromType(issue.propertyType)
                                                                                 : defaultValue, processedElement.getProject());
      PsiElement adjusted = myQuickFixAdapter.addProperty(processedElement, property);
      processedElement = adjusted;
      if (isSingle) {
        newElementRef.set(adjusted);
      }
    }
  }

  @Contract("null, _ -> null")
  public @Nullable String formatDefaultValue(@Nullable Object defaultValueObject, @NotNull Language targetLanguage) {
    if (defaultValueObject instanceof JsonSchemaObject schemaObject) {
      var renderingLanguage = targetLanguage.is(JsonLanguage.INSTANCE) ? JsonSchemaObjectRenderingLanguage.JSON : JsonSchemaObjectRenderingLanguage.YAML;
      return renderSchemaNode(schemaObject, renderingLanguage);
    }
    else if (defaultValueObject instanceof JsonNode jsonNode) {
      return convertToYamlIfNeeded(targetLanguage, jsonNode);
    }
    else if (defaultValueObject instanceof String) {
      return StringUtil.wrapWithDoubleQuote(defaultValueObject.toString());
    }
    else if (defaultValueObject instanceof Boolean) {
      return Boolean.toString((Boolean)defaultValueObject);
    }
    else if (defaultValueObject instanceof Number) {
      return defaultValueObject.toString();
    }
    else if (defaultValueObject instanceof PsiElement) {
      return ((PsiElement)defaultValueObject).getText();
    }
    return null;
  }

  private static @Nullable String convertToYamlIfNeeded(@NotNull Language language, JsonNode jsonNode) {
    JsonFactory jacksonFactory;
    if (language.is(JsonLanguage.INSTANCE))
      jacksonFactory = new JsonFactory();
    else
      jacksonFactory = YAMLFactory.builder()
        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        .build();

    try {
      var exampleInTargetLanguage =  new ObjectMapper(jacksonFactory)
        .writerWithDefaultPrettyPrinter()
        .writeValueAsString(jsonNode);
      return StringUtil.trimEnd(exampleInTargetLanguage, "\n");
    }
    catch (JsonProcessingException e) {
      return null;
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project,
                       CommonProblemDescriptor @NotNull [] descriptors,
                       @NotNull List<PsiElement> psiElementsToIgnore,
                       @Nullable Runnable refreshViews) {
    List<Pair<AddMissingPropertyFix, PsiElement>> propFixes = new ArrayList<>();
    for (CommonProblemDescriptor descriptor: descriptors) {
      if (!(descriptor instanceof ProblemDescriptor)) continue;
      QuickFix[] fixes = descriptor.getFixes();
      if (fixes == null) continue;
      AddMissingPropertyFix fix = getWorkingQuickFix(fixes);
      if (fix == null) continue;
      propFixes.add(Pair.create(fix, ((ProblemDescriptor)descriptor).getPsiElement()));
    }

    DocumentUtil.writeInRunUndoTransparentAction(() -> propFixes.forEach(fix ->
                                                   fix.first.performFix(fix.second)));
  }

  private static @Nullable AddMissingPropertyFix getWorkingQuickFix(QuickFix @NotNull [] fixes) {
    for (QuickFix fix : fixes) {
      if (fix instanceof AddMissingPropertyFix) {
        return (AddMissingPropertyFix)fix;
      }
    }
    return null;
  }
}
