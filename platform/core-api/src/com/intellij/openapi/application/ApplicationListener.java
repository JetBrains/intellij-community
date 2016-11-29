/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Listener for application events.
 */
public interface ApplicationListener extends EventListener {
  /**
   * This method is called to check whether the Application is ready to exit.
   * @return true or false
   */
  boolean canExitApplication();

  /**
   * Is called when application is exiting.
   */
  void applicationExiting();

  /**
   * Is called before action start.
   */
  void beforeWriteActionStart(@NotNull Object action);

  /**
   * Is called on action start.
   */
  void writeActionStarted(@NotNull Object action);

  /**
   *  Is called on before action finish, while while lock is still being hold 
   */
  void writeActionFinished(@NotNull Object action);

  /**
   *  Is called after action finish and lock is released
   */
  void afterWriteActionFinished(@NotNull Object action);
}