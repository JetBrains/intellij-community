// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    final List<JsonSchemaVariantsTreeBuilder.Step> position = walker.findPosition(checkable, true);
    if (position == null) return null;
    final Collection<JsonSchemaObject> schemas = new JsonSchemaResolver(rootSchema, true, position).resolve();

    for (JsonSchemaObject schema : schemas) {
      final String htmlDescription = getBestDocumentation(preferShort, schema);
      if (htmlDescription != null) return htmlDescription;
    }

    return null;
  }

  @Nullable
  public static String getBestDocumentation(boolean preferShort, @NotNull final JsonSchemaObject schema) {
    final String htmlDescription = schema.getHtmlDescription();
    final String description = schema.getDescription();
    final String title = schema.getTitle();
    if (preferShort && !StringUtil.isEmptyOrSpaces(title)) {
      return plainTextPostProcess(title);
    } else if (!StringUtil.isEmptyOrSpaces(htmlDescription)) {
      return htmlDescription;
    } else if (!StringUtil.isEmptyOrSpaces(description)) {
      return plainTextPostProcess(description);
    }
    return null;
  }

  @NotNull
  private static String plainTextPostProcess(String text) {
    return StringUtil.escapeXml(text).replace("\\n", "<br/>");
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
