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
package com.intellij.diff.requests;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MessageDiffRequest extends DiffRequest {
  @Nullable @NlsContexts.DialogTitle private String myTitle;
  @NotNull @Nls private String myMessage;

  public MessageDiffRequest(@NotNull @Nls String message) {
    this(null, message);
  }

  public MessageDiffRequest(@Nullable @NlsContexts.DialogTitle String title, @NotNull @Nls String message) {
    myTitle = title;
    myMessage = message;
  }

  @Nullable
  @Override
  public String getTitle() {
    return myTitle;
  }

  @Nls
  @NotNull
  public String getMessage() {
    return myMessage;
  }

  public void setTitle(@Nullable @NlsContexts.DialogTitle String title) {
    myTitle = title;
  }

  public void setMessage(@NotNull @Nls String message) {
    myMessage = message;
  }

  @Override
  public final void onAssigned(boolean isAssigned) {
  }
}
