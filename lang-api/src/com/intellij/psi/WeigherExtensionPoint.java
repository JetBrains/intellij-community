/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;

/**
 * @author peter
 */
public class WeigherExtensionPoint extends AbstractExtensionPointBean implements KeyedLazyInstance<Weigher> {

  // these must be public for scrambling compatibility
  @Attribute("key")
  public String key;

  @Attribute("implementationClass")
  public String implementationClass;

  @Attribute("id")
  public String id;

  private final NotNullLazyValue<Weigher> myHandler = new NotNullLazyValue<Weigher>() {
    @NotNull
    protected final Weigher compute() {
      try {
        Class<Weigher> tClass = findClass(implementationClass);
        Constructor<Weigher> constructor = tClass.getConstructor();
        constructor.setAccessible(true);
        final Weigher weigher = tClass.newInstance();
        weigher.setDebugName(id);
        return weigher;
      }
      catch (InstantiationException e) {
        throw new RuntimeException(e);
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
      catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
  };

  public Weigher getInstance() {
    return myHandler.getValue();
  }

  public String getKey() {
    return key;
  }
}
