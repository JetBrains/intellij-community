package com.jetbrains.jsonSchema.impl;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ObjectUtils;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;


public class JsonSchemaDocumentationProvider implements DocumentationProvider {
  @Nullable
  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return findSchemaAndGenerateDoc(element, originalElement, true);
  }

  @Nullable
  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    return null;
  }

  @Nullable
  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    return findSchemaAndGenerateDoc(element, originalElement, false);
  }

  @Nullable
  private static String findSchemaAndGenerateDoc(PsiElement element, @Nullable PsiElement originalElement, final boolean preferShort) {
    element = ObjectUtils.coalesce(originalElement, element);
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return null;
    final JsonSchemaService service = JsonSchemaService.Impl.get(element.getProject());
    final JsonSchemaObject rootSchema = service.getSchemaObject(containingFile.getViewProvider().getVirtualFile());
    if (rootSchema == null) return null;

    return generateDoc(element, rootSchema, preferShort);
  }

  @Nullable
  public static String generateDoc(@NotNull final PsiElement element,
                                   @NotNull final JsonSchemaObject rootSchema, final boolean preferShort) {
    final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element, rootSchema);
    if (walker == null) return null;

    final PsiElement checkable = walker.goUpToCheckable(element);
    if (checkable == null) return null;
    final List<JsonSchemaVariantsTreeBuilder.Step> position = walker.findPosition(checkable, true, true);

    final Collection<JsonSchemaObject> schemas = new JsonSchemaResolver(rootSchema, true, position).resolve();

    final String text = schemas.stream().filter(schema -> !StringUtil.isEmptyOrSpaces(schema.getDocumentation(preferShort)))
      .findFirst().map(schema -> schema.getDocumentation(preferShort)).orElse(null);
    // replace already-escaped back slash (\\n) instead of just \n
    return text == null ? null : StringUtil.escapeXml(text).replace("\\n", "<br/>");
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
