// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.extraction;

import com.intellij.openapi.wm.ToolWindowFactory;

/**
 * Marker interface for toolwindows whose model can be automatically extracted.
 * Such toolwindows:
 * <ul>
 * <li>Support creation of multiple independent instances</li>
 * <li>Use standard UI components</li>
 * <li>Provide final UI layout immediately after creation</li>
 * </ul>
 */
public interface ViewModelToolWindowFactory extends ToolWindowFactory {
}