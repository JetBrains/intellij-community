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
package com.intellij.dvcs.push.checkin.examples;

import com.intellij.dvcs.push.checkin.CheckinPushHandler;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;

import java.util.List;

//register it as an EP in META-INF/dvcs.xml
//todo: delete before merge into master
public class ExamplePushHandlerEmail implements CheckinPushHandler {

  public ExamplePushHandlerEmail() {
  }

  @NotNull
  @Override
  @CalledInAny
  public HandlerResult beforePushCheckin(@NotNull List<Change> selectedChanges, @NotNull ProgressIndicator indicator) {
    indicator.setText("Sending an Email to Admins...");
    idleUpdateProgress(5000, indicator);
    return HandlerResult.OK;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Email Sender";
  }

  public static void idleUpdateProgress(int milliseconds, ProgressIndicator indicator) {
    long now = System.currentTimeMillis();
    long time;
    while ((time = System.currentTimeMillis()) - now < milliseconds) {
      indicator.checkCanceled();
      indicator.setFraction((time - now) * 1.0 / milliseconds);
      try {
        Thread.sleep(100);
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
