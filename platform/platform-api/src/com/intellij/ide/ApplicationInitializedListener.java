// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * Use extension point {@code com.intellij.applicationInitializedListener} to register listener.
 * Please note - you cannot use {@link ExtensionPointName#findExtension)} because this extension point is cleared up after app loading.
 * <p>
 * Not part of {@link ApplicationLoadListener} to avoid class loading before application initialization.
 */
public interface ApplicationInitializedListener {
  /**
   * Invoked when all application level components are initialized in the same thread where components are initializing (EDT is not guaranteed).
   * Write actions and time-consuming activities are not recommended because listeners are invoked sequentially and directly affects application start time.
   */
  void componentsInitialized();
}
