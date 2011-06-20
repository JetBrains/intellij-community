/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.paths;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAnchor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class WebReferencesAnnotatorBase extends ExternalAnnotator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.paths.WebReferencesAnnotatorBase");

  protected static final WebReference[] EMPTY_ARRAY = new WebReference[0];

  @NotNull
  protected abstract WebReference[] collectWebReferences(@NotNull PsiFile file);

  @Nullable
  protected static WebReference lookForWebReference(@NotNull PsiElement element) {
    return lookForWebReference(Arrays.asList(element.getReferences()));
  }

  @SuppressWarnings("unchecked")
  @Nullable
  private static WebReference lookForWebReference(Collection<PsiReference> references) {
    for (PsiReference reference : references) {
      if (reference instanceof WebReference) {
        return (WebReference)reference;
      }
      else if (reference instanceof PsiDynaReference) {
        final WebReference webReference = lookForWebReference(((PsiDynaReference)reference).getReferences());
        if (webReference != null) {
          return webReference;
        }
      }
    }
    return null;
  }

  @Override
  public Object collectionInformation(@NotNull PsiFile file) {
    final WebReference[] references = collectWebReferences(file);
    final MyInfo[] infos = new MyInfo[references.length];

    for (int i = 0; i < infos.length; i++) {
      final WebReference reference = references[i];
      infos[i] = new MyInfo(PsiAnchor.create(reference.getElement()), reference.getRangeInElement(), reference.getValue());
    }
    return infos;
  }

  @Override
  public Object doAnnotate(Object collectedInfo) {
    final MyInfo[] infos = (MyInfo[])collectedInfo;
    for (MyInfo info : infos) {
      if (checkUrl(info.myUrl)) {
        info.myResult = true;
      }
    }
    return infos;
  }

  @Override
  public void apply(@NotNull PsiFile file, Object annotationResult, @NotNull AnnotationHolder holder) {
    final MyInfo[] infos = (MyInfo[])annotationResult;
    for (MyInfo info : infos) {
      if (!info.myResult) {
        final PsiElement element = info.myAnchor.retrieve();
        if (element != null) {
          final int start = element.getTextRange().getStartOffset();
          holder.createWarningAnnotation(
            new TextRange(start + info.myRangeInElement.getStartOffset(), start + info.myRangeInElement.getEndOffset()),
            getErrorMessage(info.myUrl));
        }
      }
    }
  }

  @NotNull
  protected abstract String getErrorMessage(@NotNull String url);

  private static boolean checkUrl(String url) {
    final HttpClient client = new HttpClient();
    client.setTimeout(3000);
    client.setConnectionTimeout(3000);
    final GetMethod method = new GetMethod(url);
    try {
      return client.executeMethod(method) == HttpStatus.SC_OK;
    }
    catch (IOException e) {
      LOG.info(e);
      return false;
    }
  }

  private static class MyInfo {
    final PsiAnchor myAnchor;
    final String myUrl;
    final TextRange myRangeInElement;

    volatile boolean myResult;

    private MyInfo(PsiAnchor anchor, TextRange rangeInElement, String url) {
      myAnchor = anchor;
      myRangeInElement = rangeInElement;
      myUrl = url;
    }
  }
}
