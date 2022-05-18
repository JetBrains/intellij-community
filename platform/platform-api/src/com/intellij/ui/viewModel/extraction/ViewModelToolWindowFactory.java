// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.extraction;

import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.ApiStatus;

/**
 * Marker interface for toolwindows whose model can be automatically extracted.
 * Such toolwindows:
 * <ul>
 * <li>Support creation of multiple independent instances</li>
 * <li>Use standard UI components</li>
 * <li>Provide final UI layout immediately after creation</li>
 * </ul>
 * Toolwindows with this interface will default to {@link ToolWindowExtractorMode#PER_CLIENT} extraction method unless overridden by extensions
 *
 * @see ToolWindowViewModelExtractor
 * @see ToolWindowExtractorEP
 */
@ApiStatus.Experimental
public interface ViewModelToolWindowFactory extends ToolWindowFactory {
}