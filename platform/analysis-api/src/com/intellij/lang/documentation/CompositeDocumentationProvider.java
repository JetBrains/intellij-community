// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.documentation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocCommentBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public final class CompositeDocumentationProvider implements DocumentationProvider, ExternalDocumentationProvider, ExternalDocumentationHandler {
  private static final Logger LOG = Logger.getInstance(CompositeDocumentationProvider.class);

  private final List<DocumentationProvider> myProviders;

  public static DocumentationProvider wrapProviders(Collection<? extends DocumentationProvider> providers) {
    ArrayList<DocumentationProvider> list = new ArrayList<>();
    for (DocumentationProvider provider : providers) {
      if (provider instanceof CompositeDocumentationProvider) {
        list.addAll(((CompositeDocumentationProvider)provider).getProviders());
      }
      else if (provider != null) {
        list.add(provider);
      }
    }
    // CompositeDocumentationProvider should be returned anyway because it
    // handles DocumentationProvider.EP as well as providers from the list
    return new CompositeDocumentationProvider(Collections.unmodifiableList(list));
  }

  private CompositeDocumentationProvider(List<DocumentationProvider> providers) {
    myProviders = providers;
  }

  @NotNull
  public List<DocumentationProvider> getAllProviders() {
    List<DocumentationProvider> result = new SmartList<>(getProviders());
    result.addAll(EP_NAME.getExtensionList());
    ContainerUtil.removeDuplicates(result);
    return result;
  }

  @NotNull
  public List<DocumentationProvider> getProviders() {
    return myProviders;
  }

  @Override
  public boolean handleExternal(PsiElement element, PsiElement originalElement) {
    for (DocumentationProvider provider : getAllProviders()) {
      if (provider instanceof ExternalDocumentationHandler &&
          ((ExternalDocumentationHandler)provider).handleExternal(element, originalElement)) {
        LOG.debug("handleExternal: ", provider);
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean handleExternalLink(PsiManager psiManager, String link, PsiElement context) {
    for (DocumentationProvider provider : getAllProviders()) {
      if (provider instanceof ExternalDocumentationHandler &&
          ((ExternalDocumentationHandler)provider).handleExternalLink(psiManager, link, context)) {
        LOG.debug("handleExternalLink: ", provider);
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean canFetchDocumentationLink(String link) {
    for (DocumentationProvider provider : getAllProviders()) {
      if (provider instanceof ExternalDocumentationHandler && ((ExternalDocumentationHandler)provider).canFetchDocumentationLink(link)) {
        LOG.debug("canFetchDocumentationLink: ", provider);
        return true;
      }
    }

    return false;
  }

  @NotNull
  @Override
  public String fetchExternalDocumentation(@NotNull String link, @Nullable PsiElement element) {
    for (DocumentationProvider provider : getAllProviders()) {
      if (provider instanceof ExternalDocumentationHandler && ((ExternalDocumentationHandler)provider).canFetchDocumentationLink(link)) {
        LOG.debug("fetchExternalDocumentation: ", provider);
        return ((ExternalDocumentationHandler)provider).fetchExternalDocumentation(link, element);
      }
    }

    throw new IllegalStateException("Unable to find a provider to fetch documentation link!");
  }

  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    for (DocumentationProvider provider : getAllProviders()) {
      String result = provider.getQuickNavigateInfo(element, originalElement);
      if (result != null) {
        LOG.debug("getQuickNavigateInfo: ", provider);
        return result;
      }
    }
    return null;
  }

  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    for (DocumentationProvider provider : getAllProviders()) {
      List<String> result = provider.getUrlFor(element, originalElement);
      if (result != null) {
        LOG.debug("getUrlFor: ", provider);
        return result;
      }
    }
    return null;
  }

  @Override
  public String generateDoc(PsiElement element, PsiElement originalElement) {
    for (DocumentationProvider provider : getAllProviders()) {
      String result = provider.generateDoc(element, originalElement);
      if (result != null) {
        LOG.debug("generateDoc: ", provider);
        return result;
      }
    }
    return null;
  }

  @Override
  public String generateHoverDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
    for (DocumentationProvider provider : getAllProviders()) {
      String result = provider.generateHoverDoc(element, originalElement);
      if (result != null) {
        LOG.debug("generateHoverDoc: ", provider);
        return result;
      }
    }
    return null;
  }

  @Override
  public @Nullable String generateRenderedDoc(@NotNull PsiDocCommentBase comment) {
    for (DocumentationProvider provider : getAllProviders()) {
      String result = provider.generateRenderedDoc(comment);
      if (result != null) {
        LOG.debug("generateRenderedDoc: ", provider);
        return result;
      }
    }
    return null;
  }

  @Override
  public void collectDocComments(@NotNull PsiFile file, @NotNull Consumer<@NotNull PsiDocCommentBase> sink) {
    for (DocumentationProvider provider : getAllProviders()) {
      provider.collectDocComments(file, sink);
    }
  }

  @Override
  public @Nullable PsiDocCommentBase findDocComment(@NotNull PsiFile file, @NotNull TextRange range) {
    for (DocumentationProvider provider : getAllProviders()) {
      PsiDocCommentBase result = provider.findDocComment(file, range);
      if (result != null) {
        LOG.debug("findDocComment: ", provider);
        return result;
      }
    }
    return null;
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    for (DocumentationProvider provider : getAllProviders()) {
      PsiElement result = provider.getDocumentationElementForLookupItem(psiManager, object, element);
      if (result != null) {
        LOG.debug("getDocumentationElementForLookupItem: ", provider);
        return result;
      }
    }
    return null;
  }

  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    for (DocumentationProvider provider : getAllProviders()) {
      PsiElement result = provider.getDocumentationElementForLink(psiManager, link, context);
      if (result != null) {
        LOG.debug("getDocumentationElementForLink: ", provider);
        return result;
      }
    }
    return null;
  }


  @Nullable
  public CodeDocumentationProvider getFirstCodeDocumentationProvider() {
    for (DocumentationProvider provider : getAllProviders()) {
      if (provider instanceof CodeDocumentationProvider) {
        LOG.debug("getFirstCodeDocumentationProvider: ", provider);
        return (CodeDocumentationProvider)provider;
      }
    }
    return null;
  }

  @Override
  public String fetchExternalDocumentation(Project project, PsiElement element, List<String> docUrls, boolean onHover) {
    for (DocumentationProvider provider : getAllProviders()) {
      if (provider instanceof ExternalDocumentationProvider) {
        final String doc = ((ExternalDocumentationProvider)provider).fetchExternalDocumentation(project, element, docUrls, onHover);
        if (doc != null) {
          LOG.debug("fetchExternalDocumentation: ", provider);
          return doc;
        }
      }
    }
    return null;
  }

  @Override
  public boolean hasDocumentationFor(PsiElement element, PsiElement originalElement) {
    for (DocumentationProvider provider : getAllProviders()) {
      if (hasUrlsFor(provider, element, originalElement)) {
        LOG.debug("handleExternal(hasUrlsFor): ", provider);
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean canPromptToConfigureDocumentation(PsiElement element) {
    for (DocumentationProvider provider : getAllProviders()) {
      if (provider instanceof ExternalDocumentationProvider &&
          ((ExternalDocumentationProvider)provider).canPromptToConfigureDocumentation(element)) {
        LOG.debug("canPromptToConfigureDocumentation: ", provider);
        return true;
      }
    }
    return false;
  }

  @Override
  public void promptToConfigureDocumentation(PsiElement element) {
    for (DocumentationProvider provider : getAllProviders()) {
      if (provider instanceof ExternalDocumentationProvider &&
          ((ExternalDocumentationProvider)provider).canPromptToConfigureDocumentation(element)) {
        ((ExternalDocumentationProvider)provider).promptToConfigureDocumentation(element);
        LOG.debug("promptToConfigureDocumentation: ", provider);
        break;
      }
    }
  }

  public static boolean hasUrlsFor(DocumentationProvider provider, PsiElement element, PsiElement originalElement) {
    final List<String> urls = provider.getUrlFor(element, originalElement);
    if (urls != null && !urls.isEmpty()) return true;
    return false;
  }

  @Nullable
  @Override
  public PsiElement getCustomDocumentationElement(@NotNull Editor editor,
                                                  @NotNull PsiFile file,
                                                  @Nullable PsiElement contextElement,
                                                  int targetOffset) {
    for (DocumentationProvider provider : getAllProviders()) {
      PsiElement element = provider.getCustomDocumentationElement(editor, file, contextElement, targetOffset);
      if (element != null) {
        LOG.debug("getCustomDocumentationElement: ", provider);
        return element;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return getProviders().toString();
  }
}
