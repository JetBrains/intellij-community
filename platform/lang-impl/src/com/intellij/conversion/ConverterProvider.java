// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.conversion;

import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this class and register the implementation as {@code project.converterProvider} extension in plugin.xml if you need to migrate
 * some project configuration files to a new format. Converters are executed before the project is loaded and operate on xml configuration
 * files directly via JDom API.
 * <p>If there is an applicable converter, a special dialog will be shown when the project is opened asking for confirmation and allowing
 * user to back up the configuration files. After the conversion is performed, older versions of the IDE won't be able to load the project
 * properly. So this extension point should be used only for major changes in the configuration format. If you want to just replace some
 * property in your service with a new one, it's better to do this in {@link com.intellij.openapi.components.PersistentStateComponent}'s
 * methods or by {@link Storage#deprecated() deprecating} its storage. Also it's very important to return {@code true} from {@link ConversionProcessor#isConversionNeeded}
 * method only if corresponding configuration was really used in the project to ensure that users of projects which aren't affected won't
 * be disturbed.</p>
 *
 * @see ProjectConverter
 */
public abstract class ConverterProvider {
  public static final ExtensionPointName<ConverterProvider> EP_NAME = new ExtensionPointName<>("com.intellij.project.converterProvider");

  protected ConverterProvider() {
  }

  public abstract @NlsContexts.DialogMessage @NotNull String getConversionDescription();

  public abstract @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull ProjectConverter createConverter(@NotNull ConversionContext context);
}
