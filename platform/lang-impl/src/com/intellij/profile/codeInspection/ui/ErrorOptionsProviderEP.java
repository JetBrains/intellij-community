// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.ConfigurableEP;
import org.jetbrains.annotations.ApiStatus;

/**
 * Register implementation of {@link ErrorOptionsProvider} in the plugin.xml to provide additional options in Editor | "Error highlighting" section:
 * <p/>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;errorOptionsProvider instance="class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p>
 * A new instance of the specified class will be created each time then the Settings dialog is opened
 */
@ApiStatus.Internal
public final class ErrorOptionsProviderEP extends ConfigurableEP<ErrorOptionsProvider> {
  public static final ExtensionPointName<ErrorOptionsProviderEP> EP_NAME = ExtensionPointName.create("com.intellij.errorOptionsProvider");
}
