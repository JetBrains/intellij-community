// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for {@link Language}-bound extension points.
 *
 * @author yole
 */
public class LanguageExtensionPoint<T> extends CustomLoadingExtensionPointBean implements KeyedLazyInstance<T> {
  public LanguageExtensionPoint() {
  }

  public LanguageExtensionPoint(@NotNull T instance) {
    myInstance = instance;
  }

  // these must be public for scrambling compatibility

  /**
   * Language ID.
   *
   * @see Language#getID()
   */
  @Attribute("language")
  public String language;

  @Attribute("implementationClass")
  public String implementationClass;

  private volatile T myInstance;

  @NotNull
  @Override
  public T getInstance() {
    T result = myInstance;
    if (result != null) {
      return result;
    }

    //noinspection SynchronizeOnThis
    synchronized (this) {
      result = myInstance;
      if (result != null) {
        return result;
      }

      //noinspection NonPrivateFieldAccessedInSynchronizedContext
      result = instantiateExtension(implementationClass, ApplicationManager.getApplication().getPicoContainer());
      myInstance = result;
    }
    return result;
  }

  @Override
  public String getKey() {
    return language;
  }
}
