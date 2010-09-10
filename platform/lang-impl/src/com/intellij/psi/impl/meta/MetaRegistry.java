/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.psi.impl.meta;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.PatternFilter;
import com.intellij.psi.meta.MetaDataContributor;
import com.intellij.psi.meta.MetaDataRegistrar;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 07.05.2003
 * Time: 3:31:09
 * To change this template use Options | File Templates.
 */
public class MetaRegistry extends MetaDataRegistrar {
  private static final List<MyBinding> ourBindings = ContainerUtil.createEmptyCOWList();
  private static volatile boolean ourContributorsLoaded = false;

  private static final Key<CachedValue<PsiMetaData>> META_DATA_KEY = Key.create("META DATA KEY");

  public static void bindDataToElement(final PsiElement element, final PsiMetaData data) {
    CachedValue<PsiMetaData> value = CachedValuesManager.getManager(element.getProject()).createCachedValue(new CachedValueProvider<PsiMetaData>() {
      public Result<PsiMetaData> compute() {
        data.init(element);
        return new Result<PsiMetaData>(data, data.getDependences());
      }
    });
    element.putUserData(META_DATA_KEY, value);
  }

  public static PsiMetaData getMeta(final PsiElement element) {
    final PsiMetaData base = getMetaBase(element);
    return base != null ? base : null;
  }

  private static final UserDataCache<CachedValue<PsiMetaData>, PsiElement, Object> ourCachedMetaCache =
    new UserDataCache<CachedValue<PsiMetaData>, PsiElement, Object>() {
      protected CachedValue<PsiMetaData> compute(final PsiElement element, Object p) {
        return CachedValuesManager.getManager(element.getProject()).createCachedValue(new CachedValueProvider<PsiMetaData>() {
          public Result<PsiMetaData> compute() {
            ensureContributorsLoaded();
            for (final MyBinding binding : ourBindings) {
              if (binding.myFilter.isClassAcceptable(element.getClass()) && binding.myFilter.isAcceptable(element, element.getParent())) {
                final PsiMetaData data;
                try {
                  data = binding.myDataClass.newInstance();
                }
                catch (InstantiationException e) {
                  throw new RuntimeException("failed to instantiate " + binding.myDataClass, e);
                }
                catch (IllegalAccessException e) {
                  throw new RuntimeException("failed to instantiate " + binding.myDataClass, e);
                }
                data.init(element);
                return new Result<PsiMetaData>(data, data.getDependences());
              }
            }
            return new Result<PsiMetaData>(null, element);
          }
        }, false);
      }
    };

  private static void ensureContributorsLoaded() {
    if (!ourContributorsLoaded) {
      synchronized (ourBindings) {
        if (!ourContributorsLoaded) {
          for(MetaDataContributor contributor: Extensions.getExtensions(MetaDataContributor.EP_NAME)) {
            contributor.contributeMetaData(MetaDataRegistrar.getInstance());
          }
          ourContributorsLoaded = true;
        }
      }
    }
  }
  
  @Nullable
  public static PsiMetaData getMetaBase(final PsiElement element) {
    ProgressManager.checkCanceled();
    return ourCachedMetaCache.get(META_DATA_KEY, element, null).getValue();
  }

  public static <T extends PsiMetaData> void addMetadataBinding(ElementFilter filter, Class<T> aMetadataClass, Disposable parentDisposable) {
    final MyBinding binding = new MyBinding(filter, aMetadataClass);
    addBinding(binding);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        ourBindings.remove(binding);
      }
    });
  }

  public static <T extends PsiMetaData> void addMetadataBinding(ElementFilter filter, Class<T> aMetadataClass) {
    addBinding(new MyBinding(filter, aMetadataClass));
  }

  private static void addBinding(final MyBinding binding) {
    ourBindings.add(0, binding);
  }

  public <T extends PsiMetaData> void registerMetaData(ElementFilter filter, Class<T> metadataDescriptorClass) {
    addMetadataBinding(filter, metadataDescriptorClass);
  }

  @Override
  public <T extends PsiMetaData> void registerMetaData(ElementPattern<?> pattern, Class<T> metadataDescriptorClass) {
    addMetadataBinding(new PatternFilter(pattern), metadataDescriptorClass);
  }

  private static class MyBinding {
    private final ElementFilter myFilter;
    private final Class<? extends PsiMetaData> myDataClass;

    public MyBinding(@NotNull ElementFilter filter, @NotNull Class<? extends PsiMetaData> dataClass) {
      myFilter = filter;
      myDataClass = dataClass;
    }
  }
}
