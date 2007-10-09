/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.util.concurrency.readwrite;

import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;

public class CommandWaiter extends AbstractWaiter implements CommandListener {

  private Runnable myCommandRunnable;

  public CommandWaiter(Runnable aCommandRunnable) {
    myCommandRunnable = aCommandRunnable;

    setFinished(false);
    CommandProcessor.getInstance().addCommandListener(this);
  }

  public void beforeCommandFinished(CommandEvent event) {
  }

  public void commandFinished(CommandEvent event) {
    if (event.getCommand() == myCommandRunnable) {
      CommandProcessor.getInstance().removeCommandListener(this);
      setFinished(true);
    }
  }

  public void commandStarted(CommandEvent event) {
  }

  public void undoTransparentActionStarted() {
  }

  public void undoTransparentActionFinished() {
  }
}
