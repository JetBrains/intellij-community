/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.lang.documentation;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class CompositeDocumentationProvider extends ExtensibleDocumentationProvider{

  private final List<DocumentationProvider> myProviders;

  public CompositeDocumentationProvider (DocumentationProvider ... documentationProviders) {
    this(Arrays.asList(documentationProviders));
  }

  public CompositeDocumentationProvider(Collection<DocumentationProvider> providers) {
    myProviders = new ArrayList<DocumentationProvider>(providers);
  }

  public void inject (DocumentationProvider provider) {
    myProviders.add ( provider );
  }

  public void remove (DocumentationProvider provider) {
    myProviders.remove ( provider );
  }

  public String getQuickNavigateInfo(PsiElement element) {
    for ( DocumentationProvider provider : myProviders ) {
      String result = provider.getQuickNavigateInfo(element);
      if ( result != null ) return result;
    }
    return super.getQuickNavigateInfo(element);
  }

  public String getUrlFor(PsiElement element, PsiElement originalElement) {
    for ( DocumentationProvider provider : myProviders ) {
      String result = provider.getUrlFor(element,originalElement);
      if ( result != null ) return result;
    }
    return super.getUrlFor(element, originalElement);
  }

  public String generateDoc(PsiElement element, PsiElement originalElement) {
    for ( DocumentationProvider provider : myProviders ) {
      String result = provider.generateDoc(element,originalElement);
      if ( result != null ) return result;
    }
    return super.generateDoc(element, originalElement);
  }

  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    for ( DocumentationProvider provider : myProviders ) {
      PsiElement result = provider.getDocumentationElementForLookupItem(psiManager,object,element);
      if ( result != null ) return result;
    }
    return super.getDocumentationElementForLookupItem(psiManager, object, element);
  }

  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    for ( DocumentationProvider provider : myProviders ) {
      PsiElement result = provider.getDocumentationElementForLink(psiManager,link,context);
      if ( result != null ) return result;
    }
    return super.getDocumentationElementForLink(psiManager, link, context);
  }

  @Override
  public boolean isExternalDocumentationEnabled(final PsiElement element, final PsiElement originalElement) {
    for (DocumentationProvider provider : myProviders) {
      if (provider instanceof ExtensibleDocumentationProvider && ((ExtensibleDocumentationProvider)provider).isExternalDocumentationEnabled(element,
                                                                                                                                            originalElement)) return true;
      final String url = provider.getUrlFor(element, originalElement);
      if (url != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void openExternalDocumentation(final PsiElement element, final PsiElement originalElement) {
    for (DocumentationProvider provider : myProviders) {
      if (provider instanceof ExtensibleDocumentationProvider && ((ExtensibleDocumentationProvider)provider).isExternalDocumentationEnabled(element,
                                                                                                                                            originalElement)) {
        ((ExtensibleDocumentationProvider)provider).openExternalDocumentation(element, originalElement);
        return;
      }
      final String url = provider.getUrlFor(element, originalElement);
      if (url != null) {
        BrowserUtil.launchBrowser(url);
        return;
      }
    }
  }

  @Override
  public String getExternalDocumentation(@NotNull final String url, final Project project) throws Exception {
    for (DocumentationProvider provider : myProviders) {
      if (provider instanceof ExtensibleDocumentationProvider) {
        final String externalDocumentation = ((ExtensibleDocumentationProvider)provider).getExternalDocumentation(url, project);
        if (externalDocumentation != null) return externalDocumentation;
      }
    }
    return null;
  }
}
