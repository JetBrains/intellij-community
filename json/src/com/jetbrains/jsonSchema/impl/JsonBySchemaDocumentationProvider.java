package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonProperty;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class JsonBySchemaDocumentationProvider implements DocumentationProvider {

  @NotNull
  private final JsonSchemaObject myRootSchema;

  public JsonBySchemaDocumentationProvider(@NotNull JsonSchemaObject schema) {
    myRootSchema = schema;
  }

  @Nullable
  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return null;
  }

  @Nullable
  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    return null;
  }

  @Nullable
  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (element instanceof JsonProperty) {
      final Ref<String> result = Ref.create();
      final JsonProperty jsonProperty = (JsonProperty)element;
      final String propertyName = jsonProperty.getName();
      JsonSchemaWalker.findSchemasForCompletion(jsonProperty, new JsonSchemaWalker.CompletionSchemesConsumer() {
        @Override
        public void consume(boolean isName, @NotNull JsonSchemaObject schema) {
          JsonSchemaPropertyProcessor.process(new JsonSchemaPropertyProcessor.PropertyProcessor() {
            @Override
            public boolean process(String name, JsonSchemaObject schema) {
              if (propertyName.equals(name) && schema != null) {
                result.set(schema.getDescription());
                return false;
              }

              return true;
            }
          }, schema);
        }
      }, myRootSchema);

      return result.get();
    }

    return null;
  }

  @Nullable
  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return null;
  }

  @Nullable
  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return null;
  }
}
