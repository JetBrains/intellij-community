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

package com.intellij.openapi.command.impl;

import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ContentChange;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.revertion.ChangeRevertionVisitor;

public class ChangeRangeRevertionVisitor extends ChangeRevertionVisitor {
  private final Change myFromChange;
  private final Change myToChange;

  private boolean isReverting;

  public ChangeRangeRevertionVisitor(IdeaGateway gw, Change from, Change to) {
    super(gw);
    myFromChange = from;
    myToChange = to;
  }

  @Override
  protected boolean shouldRevert(Change c) {
    if (c == myFromChange) {
      isReverting = true;
    }
    return isReverting && !(c instanceof ContentChange);
  }

  @Override
  protected void checkShouldStop(Change c) throws StopVisitingException {
    if (c == myToChange) stop();
  }
}