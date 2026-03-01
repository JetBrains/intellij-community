// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.fixes;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInsight.template.impl.EmptyNode;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.CompleteMacro;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.json.JsonBundle;
import com.intellij.json.JsonLanguage;
import com.intellij.lang.Language;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandBatchQuickFix;
import com.intellij.modcommand.ModTemplateBuilder;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
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

public final class AddMissingPropertyFix extends ModCommandBatchQuickFix {
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
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull List<ProblemDescriptor> descriptors) {
    if (descriptors.isEmpty()) return ModCommand.nop();
    return ModCommand.psiUpdate(ActionContext.from(descriptors.get(0)), updater -> {
      if (descriptors.size() == 1) {
        ProblemDescriptor descriptor = descriptors.get(0);
        QuickFix[] fixes = descriptor.getFixes();
        AddMissingPropertyFix fix = fixes == null ? this : getWorkingQuickFix(fixes);
        if (fix == null) return;
        PsiElement elementCopy = updater.getWritable(descriptor.getPsiElement());

        PsiFile file = elementCopy.getContainingFile();
        // Reformatting the whole file because formatting is crucial for yaml files and
        // changes now should be inside ModCommand.
        // Reformatting inside `myQuickFixAdapter` doesn't work for non-physical elements
        CodeStyleManager.getInstance(project).reformatText(file, 0, file.getTextLength());
        Ref<PsiElement> newElementRef = Ref.create();
        fix.performFixInner(elementCopy, newElementRef);
        PsiElement newElement = newElementRef.get();
        if (newElement == null) return; // multiple properties: no template/caret move

        JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(newElement);
        if (walker == null) return;

        Collection<JsonValueAdapter> values = Objects.requireNonNull(walker.getParentPropertyAdapter(newElement)).getValues();
        PsiElement value;
        if (values.size() == 1) value = values.iterator().next().getDelegate();
        else value = null;
        if (value == null || value.getText().isBlank()) {
          updater.moveCaretTo(newElement.getTextRange().getEndOffset());
          return;
        }
        String text = value.getText();
        boolean isEmptyArray = StringUtil.equalsIgnoreWhitespaces(text, "[]");
        boolean isEmptyObject = StringUtil.equalsIgnoreWhitespaces(text, "{}");
        boolean goInside = isEmptyArray || isEmptyObject || StringUtil.isQuotedString(text);
        TextRange range = goInside ? TextRange.create(1, text.length() - 1) : TextRange.create(0, text.length());
        var expr = fix.myData.myMissingPropertyIssues.iterator().next().enumItemsCount > 1 || isEmptyObject
          ? new MacroCallNode(new CompleteMacro())
          : (isEmptyArray ? new EmptyNode() : new ConstantNode(goInside ? StringUtil.unquoteString(text) : text));

        updater.moveCaretTo(newElement.getTextRange().getStartOffset());
        ModTemplateBuilder builder = updater.templateBuilder();
        builder.field(value, range, "VALUE", expr);
        builder.finishAt(newElement.getTextRange().getEndOffset());
      } else {
        for (ProblemDescriptor d : descriptors) {
          QuickFix[] fixes = d.getFixes();
          AddMissingPropertyFix fix = fixes == null ? this : getWorkingQuickFix(fixes);
          if (fix == null) continue;
          PsiElement elementCopy = updater.getWritable(d.getPsiElement());
          fix.performFixInner(elementCopy, Ref.create());
        }
      }
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
    PsiElement context = element instanceof PsiFile ? element.getFirstChild() : element;
    if (context == null) return;
    PsiElement processedElement = context;
    List<JsonValidationError.MissingPropertyIssueData> reverseOrder
      = ContainerUtil.reverse(new ArrayList<>(myData.myMissingPropertyIssues));
    for (JsonValidationError.MissingPropertyIssueData issue: reverseOrder) {
      Object defaultValueObject = issue.defaultValue;
      String defaultValue = formatDefaultValue(defaultValueObject, context.getLanguage());
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


  private static @Nullable AddMissingPropertyFix getWorkingQuickFix(QuickFix @NotNull [] fixes) {
    for (QuickFix fix : fixes) {
      if (fix instanceof AddMissingPropertyFix) {
        return (AddMissingPropertyFix)fix;
      }
    }
    return null;
  }
}
