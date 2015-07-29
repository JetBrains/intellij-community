/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.execution.filters.UrlFilter;
import com.intellij.openapi.paths.GlobalPathReferenceProvider;
import com.intellij.openapi.paths.PathReferenceManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;

/**
* Created by Maxim.Mossienko on 12/19/2014.
*/
public class ArbitraryPlaceUrlReferenceProvider extends PsiReferenceProvider {
  public static final ArbitraryPlaceUrlReferenceProvider INSTANCE = new ArbitraryPlaceUrlReferenceProvider();

  private static final UserDataCache<CachedValue<PsiReference[]>, PsiElement, Object> ourRefsCache = new UserDataCache<CachedValue<PsiReference[]>, PsiElement, Object>("psielement.url.refs") {
    private final AtomicReference<GlobalPathReferenceProvider> myReferenceProvider = new AtomicReference<GlobalPathReferenceProvider>();

    @Override
    protected CachedValue<PsiReference[]> compute(final PsiElement element, Object p) {
      return CachedValuesManager
        .getManager(element.getProject()).createCachedValue(new CachedValueProvider<PsiReference[]>() {
          public Result<PsiReference[]> compute() {
            Matcher matcher = UrlFilter.URL_PATTERN.matcher(element.getText());

            List<PsiReference> refs = null;
            GlobalPathReferenceProvider provider = myReferenceProvider.get();

            while (matcher.find()) {
              final int start = matcher.start();
              final int end = matcher.end();
              if (refs == null) refs = new SmartList<PsiReference>();
              if (provider == null) {
                provider = (GlobalPathReferenceProvider)PathReferenceManager.getInstance().getGlobalWebPathReferenceProvider();
                myReferenceProvider.lazySet(provider);
              }

              provider.createUrlReference(element, matcher.group(0), new TextRange(start, end), refs);
            }

            return new Result<PsiReference[]>(refs != null ? refs.toArray(new PsiReference[refs.size()]) : PsiReference.EMPTY_ARRAY,
                                              element);
          }
        }, false);

    }
  };

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull ProcessingContext context) {
    return ourRefsCache.get(element, null).getValue();
  }
}
