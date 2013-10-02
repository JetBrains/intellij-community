/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.execution;

/**
 * @author Vladislav.Soroka
 * @since 10/2/13
 */
public enum ProgressExecutionMode {
  /**
   * Perform synchronously using modal window without option to sent to background
   */
  MODAL_SYNC,
  /**
   * Perform asynchronously using background mode
   */
  IN_BACKGROUND_ASYNC,
  /**
   * Perform asynchronously using foreground window with option to sent to background
   */
  START_IN_FOREGROUND_ASYNC
}
