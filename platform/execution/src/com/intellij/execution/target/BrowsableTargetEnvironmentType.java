// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.ActionListener;
import java.util.function.Supplier;

/**
 * Environment type provides access to its filesystem for services like {@link com.intellij.openapi.ui.TextFieldWithBrowseButton}
 * So you can browse (possibly remote) target.
 */
public interface BrowsableTargetEnvironmentType {
  /**
   * @deprecated Implement and use {@link  BrowsableTargetEnvironmentType#createBrowser(Project, String, TextComponentAccessor, Component, Supplier, TargetBrowserHints)}
   */
  @SuppressWarnings("unused")
  @Deprecated(forRemoval = true)
  default @NotNull <T extends Component> ActionListener createBrowser(
    @NotNull Project project,
    @NlsContexts.DialogTitle String title,
    @NotNull TextComponentAccessor<T> textComponentAccessor,
    @NotNull T component,
    @NotNull Supplier<? extends TargetEnvironmentConfiguration> configurationSupplier
  ) {
    throw new UnsupportedOperationException("Please, call the other createBrowser that accepts TargetBrowserHints");
  }

  /**
   * @param textComponentAccessor where a path should be set. See {@link TextComponentAccessor#TEXT_FIELD_WHOLE_TEXT}.
   * @param component             text field component
   * @param configurationSupplier returns an environment configuration that must be supported by this configuration type
   * @param targetBrowserHints    various hints the target may or may not take into account
   * @return Action listener should be installed on "browse" button you want to show the target FS browser.
   */
  default @NotNull <T extends Component> ActionListener createBrowser(
    @NotNull Project project,
    @NlsContexts.DialogTitle String title,
    @NotNull TextComponentAccessor<T> textComponentAccessor,
    @NotNull T component,
    @NotNull Supplier<? extends TargetEnvironmentConfiguration> configurationSupplier,
    @NotNull TargetBrowserHints targetBrowserHints
  ) {
    return createBrowser(project, title, textComponentAccessor, component, configurationSupplier);
  }

  /**
   * When configurable contains both connection parameters and components using them (text fields with browsing in this case),
   * those components need to have current connection settings available, not the last applied to with {@code Configurable.apply()}.
   * <p>
   * This interface displays ability and provides an API to get connection settings, which are shown in the UI (see IDEA-255466).
   */
  interface ConfigurableCurrentConfigurationProvider {
    @NotNull TargetEnvironmentConfiguration getCurrentConfiguration();
  }
}
