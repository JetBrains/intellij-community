/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij;

import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;

/**
 * Extension points provided by IDEA core are listed here.
 */
public interface ExtensionPoints {
  /**
   * This extension point should be used instead of former application-components, project-components, module-components.
   * Extension declaration sample is as follows:
   * <pre>
   * &lt;extensions xmlns="com.intellij"&gt;
   *   &lt;component area="IDEA_PROJECT"&gt;
   *     &lt;implementation&gt;my.plugin.pagckage.MyProjectComponent&lt;/implementation&gt;
   *   &lt;/component&gt;
   * &lt;/extensions&gt;
   * </pre>
   * <p/>
   * Possible registration areas are IDEA_PROJECT, MODULE_PROJECT which stand for ProjectComponent and ModuleComponent correspondingly.
   * If area attribute is ommited the component will be registered in root area which corresponds to ApplicationComponent.
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

  @NonNls String JUNIT_PATCHER = "com.intellij.junitPatcher";

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

  @NonNls String INVALID_PROPERTY_KEY_INSPECTION_TOOL = "com.intellij.invalidPropertyKeyInspectionTool";
  @NonNls String I18N_INSPECTION_TOOL = "com.intellij.i18nInspectionTool";
  @NonNls String JAVA15_INSPECTION_TOOL = "com.intellij.java15InspectionTool";

  @NonNls String INSPECTIONS_GRAPH_ANNOTATOR = "com.intellij.refGraphAnnotator";

  @NonNls String INSPECTION_ENRTY_POINT = "com.intellij.entryPoint";

  @NonNls String DEAD_CODE_TOOL = "com.intellij.deadCode";

  @NonNls String JAVADOC_LOCAL = "com.intellij.javaDocNotNecessary";

  @NonNls String VISIBLITY_TOOL = "com.intellij.visibility";

  @NonNls String EMPTY_METHOD_TOOL = "com.intellij.canBeEmpty";

  @NonNls String ANT_BUILD_GEN = "com.intellij.antBuildGen";

  /**
   * Ant custom compiler extenstion point
   */
  @NonNls String ANT_CUSTOM_COMPILER = "com.intellij.antCustomCompiler";
}
