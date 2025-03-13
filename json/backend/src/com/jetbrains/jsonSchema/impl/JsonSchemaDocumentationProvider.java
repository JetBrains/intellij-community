// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.json.JsonBundle;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PsiFileReference;
import com.intellij.util.ObjectUtils;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import org.intellij.markdown.MarkdownParsingException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.markdown.utils.MarkdownToHtmlConverterKt.convertMarkdownToHtml;
import static com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils.guessType;

public class JsonSchemaDocumentationProvider implements DocumentationProvider {
  @Override
  public @Nullable @Nls String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return findSchemaAndGenerateDoc(element, originalElement, true, null);
  }

  @Override
  public @Nullable @Nls String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    String forcedPropName = null;
    if (element instanceof FakeDocElement) {
      forcedPropName = ((FakeDocElement)element).myAltName;
      element = ((FakeDocElement)element).myContextElement;
    }
    return findSchemaAndGenerateDoc(element, originalElement, false, forcedPropName);
  }

  public static @Nullable @Nls String findSchemaAndGenerateDoc(PsiElement element,
                                                               @Nullable PsiElement originalElement,
                                                               final boolean preferShort,
                                                               @Nullable String forcedPropName) {
    if (element instanceof FakePsiElement) return null;
    element = isWhitespaceOrComment(originalElement) ? element : ObjectUtils.coalesce(originalElement, element);
    if (originalElement != null && hasFileOrPointerReferences(originalElement.getReferences())) return null;
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return null;
    final JsonSchemaService service = JsonSchemaService.Impl.get(element.getProject());
    VirtualFile virtualFile = containingFile.getViewProvider().getVirtualFile();
    if (!service.isApplicableToFile(virtualFile)) return null;
    final JsonSchemaObject rootSchema = service.getSchemaObject(containingFile);
    if (rootSchema == null) return null;

    return generateDoc(element, rootSchema, preferShort, forcedPropName);
  }

  private static boolean hasFileOrPointerReferences(PsiReference[] references) {
    for (PsiReference reference : references) {
      if (reference instanceof PsiFileReference
          || reference instanceof JsonPointerReferenceProvider.JsonSchemaIdReference
          || reference instanceof JsonPointerReferenceProvider.JsonPointerReference) return true;
    }
    return false;
  }

  private static boolean isWhitespaceOrComment(@Nullable PsiElement originalElement) {
    return originalElement instanceof PsiWhiteSpace || originalElement instanceof PsiComment;
  }

  public static @Nullable @Nls String generateDoc(final @NotNull PsiElement element,
                                                  final @NotNull JsonSchemaObject rootSchema,
                                                  final boolean preferShort,
                                                  @Nullable String forcedPropName) {
    final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element, rootSchema);
    if (walker == null) return null;

    final PsiElement checkable = walker.findElementToCheck(element);
    if (checkable == null) return null;
    final JsonPointerPosition position = walker.findPosition(checkable, true);
    if (position == null) return null;
    if (forcedPropName != null) {
      if (isWhitespaceOrComment(element)) {
        position.addFollowingStep(forcedPropName);
      }
      else {
        if (position.isEmpty()) {
          return null;
        }
        if (position.isArray(position.size() - 1)) return null;
        position.replaceStep(position.size() - 1, forcedPropName);
      }
    }
    final Collection<JsonSchemaObject> schemas =
      new JsonSchemaResolver(element.getProject(), rootSchema, position, walker.createValueAdapter(element)).resolve();

    String htmlDescription = null;
    boolean deprecated = false;
    List<JsonSchemaType> possibleTypes = new ArrayList<>();
    for (JsonSchemaObject schema : schemas) {
      if (htmlDescription == null) {
        htmlDescription = getBestDocumentation(preferShort, schema);
        String message = schema.getDeprecationMessage();
        if (message != null) {
          if (htmlDescription == null) htmlDescription = message;
          else htmlDescription = message + "<br/>" + htmlDescription;
          deprecated = true;
        }
      }
      if (schema.getType() != null && schema.getType() != JsonSchemaType._any) {
        possibleTypes.add(schema.getType());
      }
      else if (schema.getTypeVariants() != null) {
        possibleTypes.addAll(schema.getTypeVariants());
      }
      else {
        final JsonSchemaType guessedType = guessType(schema);
        if (guessedType != null) {
          possibleTypes.add(guessedType);
        }
      }
    }

    return appendNameTypeAndApi(position, getThirdPartyApiInfo(element, rootSchema), possibleTypes, htmlDescription, deprecated, preferShort);
  }

  private static @Nullable @NlsSafe String appendNameTypeAndApi(@NotNull JsonPointerPosition position,
                                                                @NotNull String apiInfo,
                                                                @NotNull List<JsonSchemaType> possibleTypes,
                                                                @Nullable String htmlDescription,
                                                                boolean deprecated,
                                                                boolean preferShort) {
    if (position.isEmpty()) return htmlDescription;

    String name = position.getLastName();
    if (name == null) return htmlDescription;

    String type = "";
    String schemaType = JsonSchemaObjectReadingUtils.getTypesDescription(false, possibleTypes);
    if (schemaType != null) {
      type = ": " + schemaType;
    }

    String deprecationComment = deprecated ? JsonBundle.message("schema.documentation.deprecated.postfix") : "";
    if (preferShort) {
      htmlDescription = "<b>" + name + "</b>" + type + apiInfo + deprecationComment + (htmlDescription == null ? "" : ("<br/>" + htmlDescription));
    }
    else {
      htmlDescription = DocumentationMarkup.DEFINITION_START + name + type + apiInfo + deprecationComment + DocumentationMarkup.DEFINITION_END +
                        (htmlDescription == null ? "" : (DocumentationMarkup.CONTENT_START + htmlDescription + DocumentationMarkup.CONTENT_END));
    }
    return htmlDescription;
  }

  private static @NotNull String getThirdPartyApiInfo(@NotNull PsiElement element,
                                                      @NotNull JsonSchemaObject rootSchema) {
    JsonSchemaService service = JsonSchemaService.Impl.get(element.getProject());
    String apiInfo = "";
    JsonSchemaFileProvider provider = service.getSchemaProvider(rootSchema);
    if (provider != null) {
      String information = provider.getThirdPartyApiInformation();
      if (information != null) {
        apiInfo = "&nbsp;&nbsp;<i>(" + information + ")</i>";
      }
    }
    return apiInfo;
  }

  public static @Nullable String getBestDocumentation(boolean preferShort, final @NotNull JsonSchemaObject schema) {
    String htmlDescription = schema.getHtmlDescription();
    if (htmlDescription != null && hasNonTrustedProjects()) {
      htmlDescription = StringUtil.escapeXmlEntities(htmlDescription);
    }
    String markdownDescriptionAsHtml = getMarkdownDescriptionAsHtml(schema);
    String description = schema.getDescription();
    String title = schema.getTitle();

    String postProcessedDescription;
    if (preferShort && !StringUtil.isEmptyOrSpaces(title)) {
      postProcessedDescription = null;
    }
    else if (!StringUtil.isEmptyOrSpaces(htmlDescription)) {
      postProcessedDescription = htmlDescription;
    }
    else if (!StringUtil.isEmptyOrSpaces(description)) {
      postProcessedDescription = plainTextPostProcess(description);
    }
    else if (!StringUtil.isEmptyOrSpaces(markdownDescriptionAsHtml)) {
      postProcessedDescription = markdownDescriptionAsHtml;
    }
    else {
      return null;
    }
    return buildDocumentation(title, postProcessedDescription);
  }

  private static boolean hasNonTrustedProjects() {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (!TrustedProjects.isProjectTrusted(project)) {
        return true;
      }
    }
    return false;
  }

  private static @NotNull String plainTextPostProcess(@NotNull String text) {
    return StringUtil.escapeXmlEntities(text).replace("\\n", "<br/>");
  }

  private static @Nullable String buildDocumentation(@Nullable String title,
                                                     @Nullable String postProcessedDescription) {
    if (title == null) {
      if (postProcessedDescription == null) {
        return null;
      }
      else {
        return postProcessedDescription;
      }
    }
    else {
      if (postProcessedDescription == null) {
        return plainTextPostProcess(title);
      }
      else {
        return plainTextPostProcess(title) + "<br/>" + postProcessedDescription;
      }
    }
  }

  private static @Nullable String getMarkdownDescriptionAsHtml(@NotNull JsonSchemaObject schema) {
    String markdownDescription = getMarkdownDescription(schema);
    if (markdownDescription != null) {
      try {
        return convertMarkdownToHtml(markdownDescription);
      }
      catch (MarkdownParsingException e) {
        Logger.getInstance(JsonSchemaDocumentationProvider.class).error(e);
      }
    }
    return null;
  }

  private static @Nullable String getMarkdownDescription(@NotNull JsonSchemaObject schema) {
    String rawValue = schema.readChildNodeValue("markdownDescription");
    if (rawValue == null) return null;
    return StringUtil.unquoteString(rawValue, '\"');
  }

  @Override
  public @Nullable PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    if ((element instanceof JsonProperty || isWhitespaceOrComment(element) && element.getParent() instanceof JsonObject) && object instanceof String) {
      return new FakeDocElement(element instanceof JsonProperty ? ((JsonProperty)element).getNameElement() : element, StringUtil.unquoteString((String)object));
    }
    return null;
  }

  private static final class FakeDocElement extends FakePsiElement {
    private final PsiElement myContextElement;
    private final String myAltName;

    private FakeDocElement(PsiElement context, @NotNull String name) {
      myContextElement = context;
      myAltName = name;
    }

    @Override
    public PsiElement getParent() {
      return myContextElement;
    }

    @Override
    public @NotNull TextRange getTextRangeInParent() {
      return myContextElement.getTextRange().shiftLeft(myContextElement.getTextOffset());
    }

    @Override
    public @NotNull ItemPresentation getPresentation() {
      return new PresentationData(myAltName, null, null, null);
    }
  }
}
