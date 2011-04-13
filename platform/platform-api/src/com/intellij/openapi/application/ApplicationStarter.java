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
package com.intellij.openapi.application;

import com.intellij.ExtensionPoints;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;

/**
 * Implementors of this interface declared via {@link com.intellij.ExtensionPoints#APPLICATION_STARTER} contribute a
 * command line application based on IDEA platform.
 *
 * @author max
 * @see ApplicationStarterEx
 */
public interface ApplicationStarter {
  ExtensionPointName<ApplicationStarter> EP_NAME = ExtensionPointName.create(ExtensionPoints.APPLICATION_STARTER);

  /**
   * Command line switch to start with this runner. For example return "inspect" if you'd like to start app with
   * <code>idea.exe inspect</code> cmdline.
   * @return command line selector.
   */
  @NonNls
  String getCommandName();

  /**
   * Called before application initialization. Invoked in awt dispatch thread.
   * @param args cmdline arguments including declared selector. For example <code>"idea.exe inspect myproject.ipr"</code>
   * will pass <code>{"inspect", "myproject.ipr"}</code>
   */
  void premain(String[] args);

  /**
   * Called when application have been initialized. Invoked in awt dispatch thread. An application starter should take care terminating
   * JVM itself when appropriate by calling {@link java.lang.System#exit}(0);
   * @param args cmdline arguments including declared selector. For example <code>"idea.exe inspect myproject.ipr"</code>
   * will pass <code>{"inspect", "myproject.ipr"}</code>
   */
  void main(String[] args);
}
