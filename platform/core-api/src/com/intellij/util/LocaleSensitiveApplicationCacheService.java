// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.DynamicBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Locale-sensitive application-level cache service that is invalidated on locale environment change. E.g. new locale added.
 * Contains all the logic responsible for cache invalidation which may be changed in any moment.
 */
@Service
public final class LocaleSensitiveApplicationCacheService implements Disposable {
  private final ClearableLazyValue<Map<Class<?>, Object>> myMapProvider =
    ClearableLazyValue.create(() -> CollectionFactory.createConcurrentWeakMap());

  public LocaleSensitiveApplicationCacheService() {
    //todo remove this check after we made languageBundle dynamic; it's added to avoid warnings
    if (DynamicBundle.LanguageBundleEP.EP_NAME.getPoint().isDynamic()) {
      DynamicBundle.LanguageBundleEP.EP_NAME.addExtensionPointListener(new ExtensionPointListener<DynamicBundle.LanguageBundleEP>() {
        @Override
        public void extensionAdded(DynamicBundle.@NotNull LanguageBundleEP extension, @NotNull PluginDescriptor pluginDescriptor) {
          myMapProvider.drop();
        }

        @Override
        public void extensionRemoved(DynamicBundle.@NotNull LanguageBundleEP extension, @NotNull PluginDescriptor pluginDescriptor) {
          myMapProvider.drop();
        }
      }, this);
    }
  }

  @Override
  public void dispose() {
  }

  /**
   * @param clazz            - class of an object we  are trying to get (there can be only one instance)
   * @param valueComputation - computation for the value if missing in cache
   */
  public @NotNull <Data> Data getData(@NotNull Class<Data> clazz, @NotNull Supplier<Data> valueComputation) {
    //noinspection unchecked
    return (Data)myMapProvider.getValue().computeIfAbsent(clazz, it -> valueComputation.get());
  }

  public static @NotNull LocaleSensitiveApplicationCacheService getInstance() {
    return ApplicationManager.getApplication().getService(LocaleSensitiveApplicationCacheService.class);
  }
}
