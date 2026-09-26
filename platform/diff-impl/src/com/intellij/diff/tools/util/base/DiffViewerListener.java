/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.util.base;

import com.intellij.util.concurrency.annotations.RequiresEdt;

/**
 * @see DiffViewerBase#addListener(DiffViewerListener)
 */
public class DiffViewerListener {
  @RequiresEdt
  protected void onInit() {
  }

  @RequiresEdt
  protected void onDispose() {
  }

  @RequiresEdt
  protected void onBeforeRediff() {
  }

  /**
   * This is the best place to hook onto the viewer.
   * Internal state just had been updated and should be consistent.
   */
  @RequiresEdt
  protected void onAfterRediff() {
  }

  /**
   * Notifies that something in the world had changed and differences will need to be updated soon.
   */
  @RequiresEdt
  protected void onRediffAborted() {
  }
}
