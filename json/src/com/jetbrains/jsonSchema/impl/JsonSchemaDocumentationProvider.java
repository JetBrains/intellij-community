package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.jetbrains.jsonSchema.JsonSchemaFileType;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class JsonSchemaDocumentationProvider implements DocumentationProvider {
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
    final JsonSchemaObject rootSchema = service.getSchemaObject(containingFile.getViewProvider().getVirtualFile());
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

    final PsiElement checkable = walker.goUpToCheckable(element);
    if (checkable == null) return null;
    final List<JsonSchemaWalker.Step> position = walker.findPosition(checkable, true, true);

    final List<JsonSchemaObject> schemas = new SmartList<>();
    final MatchResult result;
    if (position == null || position.isEmpty()) {
      result = JsonSchemaVariantsTreeBuilder.simplify(rootSchema, rootSchema);
    } else {
      final JsonSchemaVariantsTreeBuilder builder = new JsonSchemaVariantsTreeBuilder(rootSchema, true, position, false);
      final JsonSchemaTreeNode root = builder.buildTree();
      result = MatchResult.zipTree(root);
    }
    schemas.addAll(result.mySchemas);
    schemas.addAll(result.myExcludingSchemas);

    return schemas.stream().filter(schema -> !StringUtil.isEmptyOrSpaces(schema.getDescription()))
      .findFirst().map(JsonSchemaObject::getDescription).orElse(null);
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
