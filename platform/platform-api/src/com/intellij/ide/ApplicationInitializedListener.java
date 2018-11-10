// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * Not part of {@link ApplicationLoadListener} to avoid class loading before application initialization.
 *
 * Please note - you cannot use {@link ExtensionPointName#findExtension)} because this extension point is cleared up after app loading.
 */
public interface ApplicationInitializedListener {
  ExtensionPointName<ApplicationInitializedListener> EP_NAME = ExtensionPointName.create("com.intellij.applicationInitializedListener");

  void componentsInitialized();
}
