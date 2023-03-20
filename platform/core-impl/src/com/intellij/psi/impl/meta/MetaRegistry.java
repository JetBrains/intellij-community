// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.meta;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.util.NullUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.meta.MetaDataContributor;
import com.intellij.psi.meta.MetaDataRegistrar;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public final class MetaRegistry extends MetaDataRegistrar {
  private static final Logger LOG = Logger.getInstance(MetaRegistry.class);
  private static final List<MyBinding> ourBindings = ContainerUtil.createLockFreeCopyOnWriteList();
  private static volatile boolean ourContributorsLoaded;

  public static PsiMetaData getMeta(PsiElement element) {
    return getMetaBase(element);
  }

  static {
    MetaDataContributor.EP_NAME.addChangeListener(MetaRegistry::clearBindings, ApplicationManager.getApplication());
  }

  private static void clearBindings() {
    synchronized (ourBindings) {
      ourContributorsLoaded = false;
      ourBindings.clear();
    }
  }

  private static void ensureContributorsLoaded() {
    if (!ourContributorsLoaded) {
      synchronized (ourBindings) {
        if (!ourContributorsLoaded) {
          for (MetaDataContributor contributor : MetaDataContributor.EP_NAME.getExtensionList()) {
            contributor.contributeMetaData(MetaDataRegistrar.getInstance());
          }
          ourContributorsLoaded = true;
        }
      }
    }
  }

  @Nullable
  public static PsiMetaData getMetaBase(PsiElement element) {
    ProgressIndicatorProvider.checkCanceled();
    return CachedValuesManager.getCachedValue(element, () -> {
      ensureContributorsLoaded();
      for (MyBinding binding : ourBindings) {
        if (binding.myFilter.isClassAcceptable(element.getClass()) && binding.myFilter.isAcceptable(element, element.getParent())) {
          PsiMetaData data = binding.myDataClass.get();
          data.init(element);
          Object[] dependencies = data.getDependencies();
          if (NullUtils.hasNull(dependencies)) {
            LOG.error(data + "(" + binding.myDataClass + ") provided null dependency");
          }
          return new CachedValueProvider.Result<>(data, ArrayUtil.append(dependencies, element));
        }
      }
      return new CachedValueProvider.Result<>(null, element);
    });
  }

  @Override
  public <T extends PsiMetaData> void registerMetaData(ElementFilter filter, Class<T> metadataDescriptorClass) {
    Supplier<? extends T> supplier = ()-> {
      try {
        return metadataDescriptorClass.newInstance();
      }
      catch (InstantiationException | IllegalAccessException e) {
        LOG.error(e);
      }
      return null;
    };
    ourBindings.add(0, new MyBinding(filter, supplier));
  }

  private static class MyBinding {
    private final ElementFilter myFilter;
    private final Supplier<? extends PsiMetaData> myDataClass;

    MyBinding(@NotNull ElementFilter filter, @NotNull Supplier<? extends PsiMetaData> dataClass) {
      myFilter = filter;
      myDataClass = dataClass;
    }
  }
}
