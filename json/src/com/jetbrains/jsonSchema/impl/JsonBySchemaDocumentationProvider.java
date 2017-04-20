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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.jsonSchema.JsonSchemaFileType;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class JsonBySchemaDocumentationProvider implements DocumentationProvider {
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
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return null;
    final JsonSchemaService service = JsonSchemaService.Impl.get(element.getProject());
    final JsonSchemaObject rootSchema = service.getSchemaForCodeAssistance(containingFile.getViewProvider().getVirtualFile());
    final VirtualFile schemaFile;
    if (rootSchema == null || (schemaFile = rootSchema.getSchemaFile()) == null) return null;

    if (JsonSchemaFileType.INSTANCE.equals(containingFile.getFileType())) {
      return generateForJsonSchemaFileType(element);
    }
    return generateDoc(element, rootSchema, schemaFile);
  }

  @Nullable
  public static String generateDoc(@NotNull final PsiElement element,
                                   @NotNull final JsonSchemaObject rootSchema,
                                   @NotNull final VirtualFile schemaFile) {
    final JsonLikePsiWalker walker = JsonSchemaWalker.getWalker(element, rootSchema);
    if (walker == null) return null;


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
    }, rootSchema, schemaFile);

    return result.get();
  }

  @Nullable
  private static String generateForJsonSchemaFileType(@NotNull PsiElement element) {
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
