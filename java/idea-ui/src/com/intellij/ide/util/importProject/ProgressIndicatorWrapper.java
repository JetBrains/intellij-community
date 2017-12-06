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
package com.intellij.ide.util.importProject;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public class ProgressIndicatorWrapper {
  @Nullable
  private final ProgressIndicator myIndicator;

  public ProgressIndicatorWrapper(@Nullable ProgressIndicator indicator) {
    myIndicator = indicator;
  }

  public boolean isCanceled() {
    return myIndicator != null && myIndicator.isCanceled();
  }

  public void setText(final String text) {
    if (myIndicator != null) {
      myIndicator.setText(text);
    }
  }

  public void setText2(final String text) {
    if (myIndicator != null) {
      myIndicator.setText2(text);
    }
  }

  public void setFraction(final double fraction) {
    if (myIndicator != null) {
      myIndicator.setFraction(fraction);
    }
  }

  public void pushState() {
    if (myIndicator != null) {
      myIndicator.pushState();
    }
  }

  public void popState() {
    if (myIndicator != null) {
      myIndicator.popState();
    }
  }

  public void setIndeterminate(final boolean indeterminate) {
    if (myIndicator != null) {
      myIndicator.setIndeterminate(indeterminate);
    }
  }

  public void checkCanceled() throws ProcessCanceledException {
    if (myIndicator != null) {
      myIndicator.checkCanceled();
    }
  }
}
