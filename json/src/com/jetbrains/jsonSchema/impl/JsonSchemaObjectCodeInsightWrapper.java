package com.jetbrains.jsonSchema.impl;


import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.documentation.DocumentationProvider;
import com.jetbrains.jsonSchema.extension.SchemaType;
import org.jetbrains.annotations.NotNull;

class JsonSchemaObjectCodeInsightWrapper implements CodeInsightProviders {
  @NotNull private final String myName;
  @NotNull private final SchemaType mySchemaType;

  @NotNull
  private final CompletionContributor myContributor;
  @NotNull
  private final Annotator myAnnotator;

  @NotNull
  private final DocumentationProvider myDocumentationProvider;

  public JsonSchemaObjectCodeInsightWrapper(@NotNull String name, @NotNull SchemaType type, @NotNull JsonSchemaObject schemaObject) {
    myName = name;
    mySchemaType = type;
    myContributor = new JsonBySchemaObjectCompletionContributor(type, schemaObject);
    myAnnotator = new JsonBySchemaObjectAnnotator(schemaObject);
    myDocumentationProvider = new JsonBySchemaDocumentationProvider(schemaObject);
  }

  @Override
  @NotNull
  public CompletionContributor getContributor() {
    return myContributor;
  }

  @Override
  @NotNull
  public Annotator getAnnotator() {
    return myAnnotator;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public DocumentationProvider getDocumentationProvider() {
    return myDocumentationProvider;
  }

  public boolean isUserSchema() {
    return SchemaType.userSchema.equals(mySchemaType);
  }
}
