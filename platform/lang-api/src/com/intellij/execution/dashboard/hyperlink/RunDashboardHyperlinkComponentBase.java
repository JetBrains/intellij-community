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
package com.intellij.execution.dashboard.hyperlink;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;

/**
 * @author Konstantin Aleev
 */
public abstract class RunDashboardHyperlinkComponentBase implements RunDashboardHyperlinkComponent {
  @Nullable private LinkListener myLinkListener;
  private boolean myAimed;

  public RunDashboardHyperlinkComponentBase(@Nullable LinkListener linkListener) {
    myLinkListener = linkListener;
  }

  @Override
  public void onClick(@NotNull MouseEvent event) {
    if (myLinkListener != null) {
      myLinkListener.onClick(event);
    }
  }

  public void setLinkListener(@Nullable LinkListener linkListener) {
    myLinkListener = linkListener;
  }

  @Override
  public boolean isAimed() {
    return myAimed;
  }

  @Override
  public void setAimed(boolean aimed) {
    myAimed = aimed;
  }

  public interface LinkListener {
    void onClick(@NotNull MouseEvent event);
  }
}
