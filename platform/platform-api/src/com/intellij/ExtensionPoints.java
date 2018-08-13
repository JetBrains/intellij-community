// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;

/**
 * Extension points provided by IDEA core are listed here.
 */
public interface ExtensionPoints extends ToolExtensionPoints {
  /**
   * This extension point should be used instead of former application-components, project-components, module-components.
   * Extension declaration sample is as follows:
   * <pre>
   * &lt;extensions xmlns="com.intellij"&gt;
   *   &lt;component area="IDEA_PROJECT"&gt;
   *     &lt;implementation&gt;my.plugin.package.MyProjectComponent&lt;/implementation&gt;
   *   &lt;/component&gt;
   * &lt;/extensions&gt;
   * </pre>
   * <p/>
   * Possible registration areas are IDEA_PROJECT, MODULE_PROJECT which stand for ProjectComponent and ModuleComponent correspondingly.
   * If area attribute is omitted the component will be registered in root area which corresponds to application component.
   */
  @NonNls String COMPONENT = "com.intellij.component";

  /**
   * This extension point allows a plugin vendor to provide the user ability to report fatal errors (aka exceptions) that happened in
   * their plugin code.
   * Extension declaration sample is as follows:
   * <pre>
   * &lt;extensions xmlns="com.intellij"&gt;
   *   &lt;errorHandler implementation="my.plugin.package.MyErrorHandler"/&gt;
   * &lt;/extensions&gt;
   * </pre>
   * my.plugin.package.MyErrorHandler class must implement {@link com.intellij.openapi.diagnostic.ErrorReportSubmitter} abstract class.
   */
  @NonNls String ERROR_HANDLER = "com.intellij.errorHandler";

  ExtensionPointName<ErrorReportSubmitter> ERROR_HANDLER_EP = ExtensionPointName.create(ERROR_HANDLER);

  /**
   * This extension point allows a plugin vendor to provide patches to junit run/debug configurations
   * Extension declaration sample is as follows:
   * <pre>
   * &lt;extensions xmlns="com.intellij"&gt;
   *   &lt;junitPatcher implementation="my.plugin.package.MyJUnitPatcher"/&gt;
   * &lt;/extensions&gt;
   * </pre>
   * my.plugin.package.MyJUnitPatcher class must implement {@link com.intellij.execution.JUnitPatcher} abstract class.
   */
  @SuppressWarnings("JavadocReference") @NonNls String JUNIT_PATCHER = "com.intellij.junitPatcher";

  /**
   * This extensions allows to run custom [command-line] application based on IDEA platform
   * <pre>
   * &lt;extensions xmlns="com.intellij"&gt;
   *   &lt;applicationStarter implementation="my.plugin.package.MyApplicationStarter"/&gt;
   * &lt;/extensions&gt;
   * </pre>
   * my.plugin.package.MyApplicationStarter class must implement {@link com.intellij.openapi.application.ApplicationStarter} interface.
   */
  @NonNls String APPLICATION_STARTER = "com.intellij.appStarter";

  @NonNls String ANT_BUILD_GEN = "com.intellij.antBuildGen";

  /**
   * Ant custom compiler extension point
   */
  @NonNls String ANT_CUSTOM_COMPILER = "com.intellij.antCustomCompiler";
}
