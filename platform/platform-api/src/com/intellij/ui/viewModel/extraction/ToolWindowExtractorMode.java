// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.extraction;

import org.jetbrains.annotations.ApiStatus;

/**
 * The extraction mode that determines which internal mechanism is used to transfer toolwindow contents to remote clients.<br/>
 *
 * The effective mode for a given toolwindow/client pair is determined by the following, in order of priority:
 * <ol>
 *   <li>{@code codeWithMe.toolwindow.force.extractor.mode} registry key (used for testing purposes)</li>
 *   <li>{@link ToolWindowViewModelExtractor} extensions, first applicable</li>
 *   <li>{@link ToolWindowExtractorEP} extensions</li>
 *   <li>{@link ViewModelToolWindowFactory} marker interface on toolwindow's factory (specifies {@link #PER_CLIENT})</li>
 *   <li>Default mode is used:</li>
 *   <ul>
 *     <li>{@link #MIRROR} for few common toolwindows for CWM guests</li>
 *     <li>{@link #FALLBACK} for all other toolwindows for CWM guests</li>
 *     <li>{@link #PROJECTOR_STEALING} for Remote Dev controller client</li>
 *   </ul>
 * </ol>
 */
@ApiStatus.Experimental
public enum ToolWindowExtractorMode {
  /**
   * Do not allow clients to access toolwindow.
   */
  DISABLE,
  /**
   * Use single host toolwindow, allow clients to access host toolwindow but DO NOT share its UI with clients.
   * Clients will use their own instance/contents of toolwindow, with focus on it (but not contents) being shared in follow mode.
   */
  FALLBACK,
  /**
   * Use single host toolwindow and share its UI between multiple clients using BeControl view models
   */
  MIRROR,
  /**
   * Create a separate ToolWindow instance for each client and share its UI using BeControl view models
   */
  PER_CLIENT,
  /**
   * Create a separate ToolWindow instance in a separate window that will be sent via Projector to client
   * Applicable only for Remote Dev controller client.
   */
  PROJECTOR_INSTANCING,
  /**
   * Steal content from host's toolwindow to share it using Projector.
   * Applicable only for Remote Dev controller client.
   */
  PROJECTOR_STEALING;

  /**
   * Returns true for modes that create a new toolwindow instance per client
   */
  public boolean isPerClientLike() {
    return this == PER_CLIENT || this == PROJECTOR_INSTANCING;
  }

  /**
   * Returns true for modes that share host toolwindow content with clients
   */
  public boolean isMirrorLike() {
    return this == MIRROR || this == PROJECTOR_STEALING;
  }

  /**
   * Returns true for modes that rely on Projector to transfer toolwindow contents
   * Applicable only to Remote Dev controller client
   */
  public boolean isProjected() {
    return this == PROJECTOR_INSTANCING || this == PROJECTOR_STEALING;
  }
}
