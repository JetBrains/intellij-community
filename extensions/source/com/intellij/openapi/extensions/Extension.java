/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions;

/**
 * @author kir
 *
 * An extension can implement this interface to get notifications when it is added/removed to {@link ExtensionPoint}
 */
public interface Extension {
  void extensionAdded(ExtensionPoint extensionPoint);
  void extensionRemoved(ExtensionPoint extensionPoint);
}
