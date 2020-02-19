// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.AbstractBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;
import java.util.function.Supplier;

import static com.intellij.BundleUtil.loadLanguageBundle;

public class UIBundle {
  public static final String BUNDLE = "messages.UIBundle";
  private static ResourceBundle ourBundle;

  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    ResourceBundle bundle = getBundle();
    if (!bundle.containsKey(key)) {
      return UtilUiBundle.message(key, params);
    }
    return AbstractBundle.message(bundle, key, params);
  }

  public static Supplier<String> lazyMessage(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return () -> message(key, params);
  }

  private UIBundle() { }

  private static ResourceBundle getBundle() {
    if (ourBundle != null) return ourBundle;
    return ourBundle = ResourceBundle.getBundle(BUNDLE);
  }

  public static void loadBundleFromPlugin(@Nullable ClassLoader pluginClassLoader) {
    ResourceBundle bundle = loadLanguageBundle(pluginClassLoader, BUNDLE);
    if (bundle != null) ourBundle = bundle;
  }
}