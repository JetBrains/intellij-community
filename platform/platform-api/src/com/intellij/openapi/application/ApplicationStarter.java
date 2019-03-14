// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;

/**
 * This extension point allows to run custom [command-line] application based on IDEA platform
 * <pre>
 * &lt;extensions xmlns="com.intellij"&gt;
 *   &lt;applicationStarter implementation="my.plugin.package.MyApplicationStarter"/&gt;
 * &lt;/extensions&gt;
 * </pre>
 * my.plugin.package.MyApplicationStarter class must implement {@link ApplicationStarter} interface.
 *
 * @author max
 * @see ApplicationStarterEx
 */
public interface ApplicationStarter {
  ExtensionPointName<ApplicationStarter> EP_NAME = new ExtensionPointName<>("com.intellij.appStarter");

  /**
   * Command-line switch to start with this runner.
   * For example return {@code "inspect"} if you'd like to start an app with {@code "idea.exe inspect ..."} command).
   *
   * @return command-line selector.
   */
  @NonNls
  String getCommandName();

  /**
   * Called before application initialization. Invoked in event dispatch thread.
   *
   * @param args program arguments (including the selector)
   */
  void premain(String[] args);

  /**
   * <p>Called when application has been initialized. Invoked in event dispatch thread.</p>
   * <p>An application starter should take care of terminating JVM when appropriate by calling {@link System#exit}.</p>
   *
   * @param args program arguments (including the selector)
   */
  void main(String[] args);
}
