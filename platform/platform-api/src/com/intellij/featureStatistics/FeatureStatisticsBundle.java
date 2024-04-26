// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics;

import com.intellij.BundleBase;
import com.intellij.DynamicBundle;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class FeatureStatisticsBundle {
  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return BundleBase.messageOrDefault(getBundle(key), key, null, params);
  }

  private static Reference<ResourceBundle> ourBundle;
  private static final Logger LOG = Logger.getInstance(FeatureStatisticsBundle.class);
  private static final @NonNls String BUNDLE = "messages.FeatureStatisticsBundle";

  private FeatureStatisticsBundle() {
  }

  private static ResourceBundle getBundle(final String key) {
    ResourceBundle providerBundle = ProviderBundles.INSTANCE.get(key);
    if (providerBundle != null) {
      return providerBundle;
    }

    ResourceBundle bundle = com.intellij.reference.SoftReference.dereference(ourBundle);
    if (bundle == null) {
      bundle = DynamicBundle.getResourceBundle(FeatureStatisticsBundle.class.getClassLoader(), BUNDLE);
      ourBundle = new SoftReference<>(bundle);
    }
    return bundle;
  }

  private static final class ProviderBundles extends HashMap<String, ResourceBundle> {
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static final ProviderBundles INSTANCE = new ProviderBundles();

    private ProviderBundles() {
      for (FeatureStatisticsBundleEP bundleEP : FeatureStatisticsBundleEP.EP_NAME.getExtensionList()) {
        try {
          ResourceBundle bundle = DynamicBundle.getResourceBundle(bundleEP.getPluginDescriptor().getClassLoader(), bundleEP.qualifiedName);
          for (String key : bundle.keySet()) {
            put(key, bundle);
          }
        }
        catch (MissingResourceException e) {
          LOG.error(e);
        }
      }
    }
  }
}
