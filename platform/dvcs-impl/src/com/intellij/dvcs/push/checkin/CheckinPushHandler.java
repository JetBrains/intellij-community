/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.dvcs.push.checkin;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface CheckinPushHandler {
  ExtensionPointName<CheckinPushHandler> EP_NAME = ExtensionPointName.create("com.intellij.checkingPushHandler");

  enum HandlerResult {
    OK, ABORT, ABORT_AND_CLOSE
  }

  @NotNull
  String getPresentableName();

  @CalledInAny
  @NotNull
  HandlerResult beforePushCheckin(@NotNull List<Change> selectedChanges, @NotNull ProgressIndicator indicator);

}
