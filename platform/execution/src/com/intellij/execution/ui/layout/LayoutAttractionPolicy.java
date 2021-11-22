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

package com.intellij.execution.ui.layout;

import com.intellij.ui.content.Content;
import com.intellij.execution.ui.RunnerLayoutUi;

public abstract class LayoutAttractionPolicy {

  public abstract void attract(Content content, RunnerLayoutUi ui);

  public abstract void clearAttraction(Content content, RunnerLayoutUi ui);

  public static class Bounce extends LayoutAttractionPolicy {
    @Override
    public void attract(final Content content, final RunnerLayoutUi ui) {
      ui.setBouncing(content, true);
    }

    @Override
    public void clearAttraction(final Content content, final RunnerLayoutUi ui) {
      ui.setBouncing(content, false);
    }
  }

  public static class FocusOnce extends LayoutAttractionPolicy {

    private boolean myWasAttracted;
    private final boolean myRequestFocus;

    public FocusOnce() {
      this(true);
    }

    public FocusOnce(final boolean requestFocus) {
      myRequestFocus = requestFocus;
    }

    @Override
    public void attract(final Content content, final RunnerLayoutUi ui) {
      if (!myWasAttracted) {
        myWasAttracted = true;
        ui.selectAndFocus(content, myRequestFocus, true, true);
      } else {
        ui.setBouncing(content, true);
      }
    }

    @Override
    public void clearAttraction(final Content content, final RunnerLayoutUi ui) {
      ui.setBouncing(content, false);
    }
  }

  public static class FocusAlways extends LayoutAttractionPolicy {
    @Override
    public void attract(final Content content, final RunnerLayoutUi ui) {
      ui.selectAndFocus(content, true, true);
    }

    @Override
    public void clearAttraction(final Content content, final RunnerLayoutUi ui) {
    }
  }

}