package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.jsonSchema.JsonSchemaFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class JsonBySchemaDocumentationProvider implements DocumentationProvider {

  @NotNull private final VirtualFile mySchemaFile;
  @NotNull
  private final JsonSchemaObject myRootSchema;

  public JsonBySchemaDocumentationProvider(@NotNull VirtualFile schemaFile, @NotNull JsonSchemaObject schema) {
    mySchemaFile = schemaFile;
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
    final JsonLikePsiWalker walker = JsonSchemaWalker.getWalker(element);
    if (walker == null) return null;
    final JsonProperty jsonProperty = element instanceof JsonProperty ? (JsonProperty) element : PsiTreeUtil.getParentOfType(element, JsonProperty.class);

    if (jsonProperty != null) {
      if (JsonSchemaFileType.INSTANCE.equals(jsonProperty.getContainingFile().getFileType())) {
        final JsonValue value = jsonProperty.getValue();
        if (value instanceof JsonObject) {
          final JsonProperty description = ((JsonObject)value).findProperty("description");
          if (description != null && description.getValue() instanceof JsonStringLiteral)
            return StringUtil.unquoteString(description.getValue().getText());
        }
        return null;
      }

      final Ref<String> result = Ref.create();
      final String propertyName = jsonProperty.getName();
      JsonSchemaWalker.findSchemasForCompletion(jsonProperty, walker, new JsonSchemaWalker.CompletionSchemesConsumer() {
        @Override
        public void consume(boolean isName,
                            @NotNull JsonSchemaObject schema,
                            @NotNull VirtualFile schemaFile,
                            @NotNull List<JsonSchemaWalker.Step> steps) {
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
      }, myRootSchema, mySchemaFile);

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
