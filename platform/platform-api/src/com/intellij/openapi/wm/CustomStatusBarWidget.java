// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import javax.swing.*;

/**
 * A status bar widget with a custom component.
 * <p>
 *   <em>Implementation note:</em> status bar widgets can be instantiated on a BGT,
 *   so implementations should take care not to instantiate any Swing components in constructors
 *   and/or field/property initializers. All initialization should happen in the {@link #getComponent()}
 *   method. The simplest way to achieve this is to use Kotlin's lazy initialization for the component
 *   property.
 * </p>
 */
public interface CustomStatusBarWidget extends StatusBarWidget {
  JComponent getComponent();
}
