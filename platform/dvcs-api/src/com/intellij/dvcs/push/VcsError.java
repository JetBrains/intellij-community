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
package com.intellij.dvcs.push;

import com.intellij.dvcs.ui.DvcsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsError {
  @NotNull private final @Nls String myErrorText;
  @Nullable private final VcsErrorHandler myErrorHandleListener;

  public VcsError(@NotNull @Nls String text) {
    this(text, null);
  }

  public VcsError(@NotNull @Nls String text, @Nullable VcsErrorHandler listener) {
    myErrorText = text;
    myErrorHandleListener = listener;
  }

  @Nls
  public String getText() {
    return myErrorText;
  }

  public void handleError(@NotNull CommitLoader loader) {
    if (myErrorHandleListener != null) {
      myErrorHandleListener.handleError(loader);
    }
  }

  public static VcsError createEmptyTargetError(@NotNull @Nls String name) {
    return new VcsError(DvcsBundle.message("push.error.specify.not.empty.remote.push.path.0", name));
  }
}
