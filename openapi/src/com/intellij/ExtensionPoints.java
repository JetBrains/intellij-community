package com.intellij;

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
   *
   * Possible registration areas are IDEA_PROJECT, MODULE_PROJECT which stand for ProjectComponent and ModuleComponent correspondingly.
   * If area attribute is ommited the component will be registered in root area which corresponds to ApplicationComponent.
   */
  String COMPONENT = "com.intellij.component";

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
  String ERROR_HANDLER = "com.intellij.errorHandler";

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

  String JUNIT_PATCHER = "com.intellij.junitPatcher";
}
