/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.application;

import com.intellij.ExtensionPoints;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;

/**
 * Implementers of the interface declared via {@link com.intellij.ExtensionPoints#APPLICATION_STARTER}
 * contribute to a command-line processing capability of an application.
 *
 * @author max
 * @see ApplicationStarterEx
 */
public interface ApplicationStarter {
  ExtensionPointName<ApplicationStarter> EP_NAME = ExtensionPointName.create(ExtensionPoints.APPLICATION_STARTER);

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
