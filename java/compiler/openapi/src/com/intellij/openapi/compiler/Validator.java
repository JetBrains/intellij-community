// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.compiler;

import com.intellij.diagnostic.PluginException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * An interface for compilers which validate something after the compilation finishes. The validators are disabled by default and can be
 * enabled by user in File | Settings | Build, Execution, Deployment | Compiler | Validation. It's better to implement validation as inspection,
 * in that case you can use {@link com.intellij.openapi.compiler.util.InspectionValidator} extension point to allow users run the inspection
 * after a build finishes.
 *
 * <p>
 * The implementation of this class should be registered in plugin.xml:
 * <pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&lt;compiler implementation="qualified-class-name"/&gt;
 * &lt;/extensions&gt;
 * </pre>
 * </p>
 * </p>
 */
public interface Validator extends FileProcessingCompiler {
  /**
   * Returns unique ID, which can be used in project configuration files.
   */
  default @NotNull @NonNls String getId() {
    PluginException.reportDeprecatedDefault(
      getClass(), "getId",
      "The default implementation delegates to 'getDescription' which may be localized," +
      " but return value of this method must not depend on current localization.");
    return getDescription();
  }
}
