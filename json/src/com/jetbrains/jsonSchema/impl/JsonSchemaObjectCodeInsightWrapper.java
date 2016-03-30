package com.jetbrains.jsonSchema.impl;


import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.documentation.DocumentationProvider;
import org.jetbrains.annotations.NotNull;

class JsonSchemaObjectCodeInsightWrapper implements CodeInsightProviders {
  @NotNull private final String myName;
  private boolean myIsUserSchema;

  @NotNull
  private final CompletionContributor myContributor;
  @NotNull
  private final Annotator myAnnotator;

  @NotNull
  private final DocumentationProvider myDocumentationProvider;

  public JsonSchemaObjectCodeInsightWrapper(@NotNull String name, @NotNull JsonSchemaObject schemaObject) {
    myName = name;
    myContributor = new JsonBySchemaObjectCompletionContributor(schemaObject);
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

  public JsonSchemaObjectCodeInsightWrapper setUserSchema(boolean userSchema) {
    myIsUserSchema = userSchema;
    return this;
  }

  public boolean isUserSchema() {
    return myIsUserSchema;
  }
}
