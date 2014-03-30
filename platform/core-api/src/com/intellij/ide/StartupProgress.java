/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide;

/**
 * @author Dmitry Avdeev
 *         Date: 7/21/11
 */
public interface StartupProgress {

  /**
   * Displays new progress state.
   * @param message text to be shown
   * @param progress progress state from 0 to 1
   */
  void showProgress(String message, float progress);
}
