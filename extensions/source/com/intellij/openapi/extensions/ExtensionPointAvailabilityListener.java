/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions;

/**
 * @author AKireyev
 */
public interface ExtensionPointAvailabilityListener {
  void extensionPointRegistered(ExtensionPoint extensionPoint);
  void extensionPointRemoved(ExtensionPoint extensionPoint);
}
