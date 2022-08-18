// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.extraction;

import com.intellij.codeWithMe.ClientId;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This interface is used in Code With Me and unattended mode (Remote Dev) to determine how a given toolwindow is networked for a given client.<br/>
 * If multiple extensions are registered, the first applicable one will be used for a given toolwindow/client combination.
 * Thus, it's not recommended to implement catch-all extractors. <br/>
 * See {@link ToolWindowExtractorMode} for extra information, available modes, and their meaning
 *
 * @see ToolWindowExtractorEP
 * @see ViewModelToolWindowFactory
 */
@ApiStatus.Experimental
public interface ToolWindowViewModelExtractor {
  ExtensionPointName<ToolWindowViewModelExtractor> EP_NAME = ExtensionPointName.create("com.intellij.toolWindowExtractor");

  /**
   * Decides whether this extractor is applicable to a given toolwindow for a given client
   * @param toolWindowId the internal ID of toolwindow to potentially be shared
   * @param clientId the client for which the toolwindow would be shared
   * @return true, if this extractor's {@link #getMode} should be applied. false, otherwise (other extensions will be queried)
   */
  boolean isApplicable(@NotNull String toolWindowId, @Nullable ClientId clientId);

  /**
   * The extraction mode that this extractor requests for applicable toolwindows/clients.
   * @see ToolWindowExtractorMode
   */
  @NotNull
  ToolWindowExtractorMode getMode();
}