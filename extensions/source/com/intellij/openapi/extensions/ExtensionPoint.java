/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions;

/**
 * @author AKireyev
 */
public interface ExtensionPoint {
  String getName();
  AreaInstance getArea();

  String getBeanClassName();

  void registerExtension(Object extension);
  void registerExtension(Object extension, LoadingOrder order);

  Object[] getExtensions();
  Object getExtension();
  boolean hasExtension(Object extension);

  void unregisterExtension(Object extension);

  void addExtensionPointListener(ExtensionPointListener listener);
  void removeExtensionPointListener(ExtensionPointListener extensionPointListener);

  void reset();

  Class getExtensionClass();
}
