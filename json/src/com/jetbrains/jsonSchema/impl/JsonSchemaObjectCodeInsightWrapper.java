package com.jetbrains.jsonSchema.impl;


import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairConsumer;
import com.intellij.util.Processor;
import com.jetbrains.jsonSchema.extension.SchemaType;
import org.jetbrains.annotations.NotNull;

class JsonSchemaObjectCodeInsightWrapper implements CodeInsightProviders {
  @NotNull private final String myName;
  @NotNull private final SchemaType mySchemaType;
  @NotNull private final VirtualFile mySchemaFile;
  @NotNull private final JsonSchemaObject mySchemaObject;

  @NotNull
  private final CompletionContributor myContributor;
  @NotNull
  private final Annotator myAnnotator;

  @NotNull
  private final DocumentationProvider myDocumentationProvider;


  public JsonSchemaObjectCodeInsightWrapper(@NotNull Project project, @NotNull String name,
                                            @NotNull SchemaType type,
                                            @NotNull VirtualFile schemaFile,
                                            @NotNull JsonSchemaObject schemaObject) {
    myName = name;
    mySchemaType = type;
    mySchemaFile = schemaFile;
    mySchemaObject = schemaObject;
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

  @Override
  public boolean iterateSchemaObjects(@NotNull final Processor<JsonSchemaObject> consumer) {
    return consumer.process(mySchemaObject);
  }

  @Override
  public void iterateSchemaFiles(@NotNull final PairConsumer<VirtualFile, String> consumer) {
    consumer.consume(mySchemaFile, mySchemaObject.getId());
  }

  public boolean isUserSchema() {
    return SchemaType.userSchema.equals(mySchemaType);
  }
}
