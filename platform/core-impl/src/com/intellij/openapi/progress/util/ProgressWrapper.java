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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Sep 26, 2007
 * Time: 1:56:28 PM
 */
package com.intellij.openapi.progress.util;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProgressWrapper extends AbstractProgressIndicatorBase {
  private final ProgressIndicator myOriginal;

  protected ProgressWrapper(@NotNull ProgressIndicator original) {
    myOriginal = original;
  }

  @Override
  public boolean isCanceled() {
    return myOriginal.isCanceled();
  }

  @NotNull
  public ProgressIndicator getOriginalProgressIndicator() {
    return myOriginal;
  }

  @Nullable
  public static ProgressWrapper wrap(@Nullable ProgressIndicator indicator) {
    return indicator == null || indicator instanceof ProgressWrapper ? (ProgressWrapper)indicator : new ProgressWrapper(indicator);
  }

  public static ProgressIndicator unwrap(ProgressIndicator indicator) {
    return indicator instanceof ProgressWrapper ?
           ((ProgressWrapper)indicator).getOriginalProgressIndicator() : indicator;
  }
}
