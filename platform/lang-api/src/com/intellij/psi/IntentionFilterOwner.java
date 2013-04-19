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

package com.intellij.psi;

import com.intellij.codeInsight.intention.IntentionAction;

public interface IntentionFilterOwner {
  /**
   * Sets the intention actions filter which is used to determine which intention actions should be available in an editor.
   *
   * @param filter the intention actions filter instance.
   */
  void setIntentionActionsFilter(IntentionActionsFilter filter);

  /**
   * Sets the intention actions filter which is used to determine which intention actions should be available in an editor.
   *
   * @return the intention actions filter instance.
   */
  IntentionActionsFilter getIntentionActionsFilter();

  /**
   * Interface to control the available intention actions.
   */
  interface IntentionActionsFilter {

    /**
     * Checks if the intention action should be available in an editor.
     * @param intentionAction the intention action to analyze
     * @return Returns true if the intention action should be available, false otherwise
     */
    boolean isAvailable(final IntentionAction intentionAction);

    /**
     * This filter reports all intentions are available.
     */
    IntentionActionsFilter EVERYTHING_AVAILABLE = new IntentionActionsFilter() {
      @Override
      public boolean isAvailable(final IntentionAction intentionAction) {
        return true;
      }
    };
  }
}
