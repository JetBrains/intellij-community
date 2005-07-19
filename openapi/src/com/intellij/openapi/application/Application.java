/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;

import java.awt.*;

/**
 * Application interface class.
 */
public interface Application extends ComponentManager {
  /**
   * Runs read action if no write actions are running, otherwise action is locked until write action is completed.<br>
   * See <a href=../../../../../readwriteactions.html>readwriteactions.html</a> for additional information.
   *
   * @param action the action to run
   */
  void runReadAction(Runnable action);

  /**
   * Runs computation in read action if no write actions are running,
   * otherwise action is locked until write action is completed.<br>
   * See <a href=../../../../../readwriteactions.html>readwriteactions.html</a> for additional information.
   *
   * @param computation the computation to perform
   */
  <T> T runReadAction(Computable<T> computation);

  /**
   * Runs write action if no read actions are running, otherwise action lock occures.<br>
   * See <a href=../../../../../readwriteactions.html>readwriteactions.html</a> for additional information.
   *
   * @param action the action to run
   */
  void runWriteAction(Runnable action);

  /**
   * Runs computation in write action if no read actions are running, otherwise action lock occures.<br>
   * See <a href=../../../../../readwriteactions.html>readwriteactions.html</a> for additional information.
   *
   * @param computation the computation to run
   */
  <T> T runWriteAction(Computable<T> computation);

  Object getCurrentWriteAction(Class actionClass);

  /**
   * Asserts whether read access is allowed.
   */
  void assertReadAccessAllowed();

  /**
   * Asserts whether write access is allowed.
   */
  void assertWriteAccessAllowed();

  /**
   * Assert whether the method is being called from event dispatch thread.
   */
  void assertIsDispatchThread();

  /**
   * Adds {@link ApplicationListener}.
   *
   * @param listener the listener to add
   */
  void addApplicationListener(ApplicationListener listener);

  /**
   * Removes {@link ApplicationListener}.
   *
   * @param listener the listener to remove
   */
  void removeApplicationListener(ApplicationListener listener);

  void saveAll();

  void saveSettings();

  void exit();

  boolean isWriteAccessAllowed();

  boolean isReadAccessAllowed();

  boolean runProcessWithProgressSynchronously(Runnable process,
                                              String progressTitle,
                                              boolean canBeCanceled,
                                              Project project);

  void invokeLater(Runnable runnable);
  void invokeLater(Runnable runnable, ModalityState state);
  void invokeAndWait(Runnable runnable, ModalityState modalityState);

  ModalityState getCurrentModalityState();
  ModalityState getModalityStateForComponent(Component c);
  ModalityState getDefaultModalityState();
  ModalityState getNoneModalityState();

  long getStartTime();
  long getIdleTime();

  boolean isUnitTestMode();
  boolean isHeadlessEnvironment();

  boolean isDispatchThread();
}
