// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;


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
  public static String findSchemaAndGenerateDoc(PsiElement element, @Nullable PsiElement originalElement, final boolean preferShort) {
    element = ObjectUtils.coalesce(originalElement, element);
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return null;
    final JsonSchemaService service = JsonSchemaService.Impl.get(element.getProject());
    VirtualFile virtualFile = containingFile.getViewProvider().getVirtualFile();
    if (!service.isApplicableToFile(virtualFile)) return null;
    final JsonSchemaObject rootSchema = service.getSchemaObject(virtualFile);
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

    String htmlDescription = null;
    List<JsonSchemaType> possibleTypes = ContainerUtil.newArrayList();
    for (JsonSchemaObject schema : schemas) {
      if (htmlDescription == null) {
        htmlDescription = getBestDocumentation(preferShort, schema);
      }
      if (schema.getType() != null && schema.getType() != JsonSchemaType._any) {
        possibleTypes.add(schema.getType());
      }
      else if (schema.getTypeVariants() != null) {
        possibleTypes.addAll(schema.getTypeVariants());
      }
    }

    return htmlDescription == null
           ? null
           : appendNameTypeAndApi(position, getThirdPartyApiInfo(element, rootSchema), possibleTypes, htmlDescription);
  }

  @Nullable
  private static String concatTypeInfo(@NotNull List<JsonSchemaType> possibleTypes) {
    if (possibleTypes.size() == 0) return null;
    if (possibleTypes.size() == 1) return possibleTypes.get(0).getDescription();

    return StringUtil.join(possibleTypes.stream().map(t -> t.getDescription()).distinct().sorted().collect(Collectors.toList()), " | ");
  }
  @NotNull
  private static String appendNameTypeAndApi(@NotNull List<JsonSchemaVariantsTreeBuilder.Step> position,
                                             @NotNull String apiInfo,
                                             @NotNull List<JsonSchemaType> possibleTypes,
                                             @NotNull String htmlDescription) {
    if (position.size() == 0) return htmlDescription;

    JsonSchemaVariantsTreeBuilder.Step lastStep = position.get(position.size() - 1);
    String name = lastStep.getName();
    if (name == null) return htmlDescription;

    String type = "";
    String schemaType = concatTypeInfo(possibleTypes);
    if (schemaType != null) {
      type = ": " + schemaType;
    }

    htmlDescription = "<b>" + name + "</b>" + type + apiInfo + "<br/><br/>" + htmlDescription;
    return htmlDescription;
  }

  @NotNull
  private static String getThirdPartyApiInfo(@NotNull PsiElement element,
                                             @NotNull JsonSchemaObject rootSchema) {
    JsonSchemaService service = JsonSchemaService.Impl.get(element.getProject());
    String apiInfo = "";
    JsonSchemaFileProvider provider = service.getSchemaProvider(rootSchema.getSchemaFile());
    if (provider != null) {
      String information = provider.getThirdPartyApiInformation();
      if (information != null) {
        apiInfo = "&nbsp;&nbsp;<i>(" + information + ")</i>";
      }
    }
    return apiInfo;
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
