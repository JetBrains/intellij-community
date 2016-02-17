package com.jetbrains.jsonSchema.impl;


import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.documentation.DocumentationProvider;
import org.jetbrains.annotations.NotNull;

class JsonSchemaObjectCodeInsightWrapper {

  @NotNull
  private final CompletionContributor myContributor;
  @NotNull
  private final Annotator myAnnotator;

  @NotNull
  private final DocumentationProvider myDocumentationProvider;

  public JsonSchemaObjectCodeInsightWrapper(@NotNull JsonSchemaObject schemaObject) {
    myContributor = new JsonBySchemaObjectCompletionContributor(schemaObject);
    myAnnotator = new JsonBySchemaObjectAnnotator(schemaObject);
    myDocumentationProvider = new JsonBySchemaDocumentationProvider(schemaObject);
  }

  @NotNull
  public CompletionContributor getContributor() {
    return myContributor;
  }

  @NotNull
  public Annotator getAnnotator() {
    return myAnnotator;
  }

  @NotNull
  public DocumentationProvider getDocumentationProvider() {
    return myDocumentationProvider;
  }
}
