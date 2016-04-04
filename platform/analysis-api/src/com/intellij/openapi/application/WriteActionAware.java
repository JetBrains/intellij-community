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

public interface WriteActionAware {
  /**
   * Indicate whether this action should be invoked inside write action.
   * Should return false if e.g. modal dialog is shown inside the action.
   * If false is returned the action itself is responsible for starting write action
   * when needed, by calling {@link Application#runWriteAction(Runnable)}.
   *
   * @return true if the action requires a write action, false otherwise.
   */
  default boolean startInWriteAction() {
    return true;
  }
}
