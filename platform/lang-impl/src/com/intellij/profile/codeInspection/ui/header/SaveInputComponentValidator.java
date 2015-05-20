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
package com.intellij.profile.codeInspection.ui.header;

import org.jetbrains.annotations.NotNull;

/**
* @author Dmitry Batkovich
*/
public interface SaveInputComponentValidator {

  void doSave(@NotNull String text);

  boolean checkValid(@NotNull String text);

  void cancel();

  class Wrapper implements SaveInputComponentValidator {
    private SaveInputComponentValidator myDelegate;
    private boolean myActive;

    public void setDelegate(SaveInputComponentValidator delegate) {
      myDelegate = delegate;
      myActive = true;
    }

    @Override
    public void doSave(@NotNull String text) {
      text = text.trim();
      if (myActive && myDelegate != null) {
        myDelegate.doSave(text);
        myActive = false;
      }
    }

    @Override
    public boolean checkValid(@NotNull String text) {
      return myActive && myDelegate != null && myDelegate.checkValid(text.trim());
    }

    @Override
    public void cancel() {
      if (myActive && myDelegate != null) {
        myDelegate.cancel();
      }
    }
  }
}
