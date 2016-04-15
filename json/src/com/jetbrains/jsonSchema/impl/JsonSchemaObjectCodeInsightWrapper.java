package com.jetbrains.jsonSchema.impl;


import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.Convertor;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.extension.schema.JsonSchemaRefReferenceProvider;
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
  private Convertor<String, PsiElement> my2SchemaResolver;

  public JsonSchemaObjectCodeInsightWrapper(@NotNull Project project, @NotNull String name,
                                            @NotNull SchemaType type,
                                            @NotNull VirtualFile schemaFile,
                                            @NotNull JsonSchemaObject schemaObject) {
    myName = name;
    mySchemaType = type;
    myContributor = new JsonBySchemaObjectCompletionContributor(type, schemaObject);
    myAnnotator = new JsonBySchemaObjectAnnotator(schemaObject);
    myDocumentationProvider = new JsonBySchemaDocumentationProvider(schemaObject);
    my2SchemaResolver = new Convertor<String, PsiElement>() {
      @Override
      public PsiElement convert(String key) {
        final PsiFile psiFile = PsiManager.getInstance(project).findFile(schemaFile);
        if (psiFile == null) return null;
        return JsonSchemaRefReferenceProvider.resolveSchemaProperty(psiFile, null, key);
      }
    };
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

  @NotNull
  @Override
  public Convertor<String, PsiElement> getToPropertyResolver() {
    return my2SchemaResolver;
  }

  public boolean isUserSchema() {
    return SchemaType.userSchema.equals(mySchemaType);
  }
}
