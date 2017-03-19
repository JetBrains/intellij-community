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
    final JsonLikePsiWalker walker = JsonSchemaWalker.getWalker(element, myRootSchema);
    if (walker == null) return null;

    if (JsonSchemaFileType.INSTANCE.equals(element.getContainingFile().getFileType())) {
      final JsonProperty jsonProperty =
        element instanceof JsonProperty ? (JsonProperty)element : PsiTreeUtil.getParentOfType(element, JsonProperty.class);
      if (jsonProperty != null) {
        final JsonValue value = jsonProperty.getValue();
        if (value instanceof JsonObject) {
          final JsonProperty description = ((JsonObject)value).findProperty("description");
          if (description != null && description.getValue() instanceof JsonStringLiteral) {
            return StringUtil.escapeXml(StringUtil.unquoteString(description.getValue().getText()));
          }
        }
        return null;
      }
    }

    final Ref<String> result = Ref.create();
    JsonSchemaWalker.findSchemasForDocumentation(element, walker, new JsonSchemaWalker.CompletionSchemesConsumer() {
      @Override
      public void consume(boolean isName,
                          @NotNull JsonSchemaObject schema,
                          @NotNull VirtualFile schemaFile,
                          @NotNull List<JsonSchemaWalker.Step> steps) {
        result.set(schema.getDescription());
      }

      @Override
      public void oneOf(boolean isName,
                        @NotNull List<JsonSchemaObject> list,
                        @NotNull VirtualFile schemaFile,
                        @NotNull List<JsonSchemaWalker.Step> steps) {
        //todo?
      }

      @Override
      public void anyOf(boolean isName,
                        @NotNull List<JsonSchemaObject> list,
                        @NotNull VirtualFile schemaFile,
                        @NotNull List<JsonSchemaWalker.Step> steps) {
        //todo?
      }
    }, myRootSchema, mySchemaFile);

    return result.get();
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
