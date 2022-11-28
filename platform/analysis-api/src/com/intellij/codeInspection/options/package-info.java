// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * API to describe the presentation of UI options for inspections.
 * <p>
 * UI Options for inspections are described in tree-like structure that uses {@link com.intellij.codeInspection.options.OptPane} as root.
 * Individual controls implement {@link com.intellij.codeInspection.options.OptControl} and layout elements implement
 * {@link com.intellij.codeInspection.options.OptComponent}. The whole UI panel can be constructed using static methods from
 * {@link com.intellij.codeInspection.options.OptPane} class (import them statically for the convenience).
 * The UI pane is independent on a particular rendering engine. Several rendering engines may be implemented separately. E.g.,
 * {@link com.intellij.codeInspection.ui.InspectionOptionPaneRenderer} could be used for Swing.
 */
package com.intellij.codeInspection.options;