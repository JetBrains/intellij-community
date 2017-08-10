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

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Aleev
 */
public class RunDashboardHyperlinkTextComponent extends RunDashboardHyperlinkComponentBase {
  private String myText;
  private boolean mySelected;
  private boolean myBold;

  public RunDashboardHyperlinkTextComponent(@Nullable RunDashboardHyperlinkComponentBase.LinkListener listener, @NotNull String text) {
    super(listener);
    myText = text;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  public void setText(@NotNull String text) {
    myText = text;
  }

  public void setSelected(boolean selected) {
    mySelected = selected;
  }

  public void setBold(boolean bold) {
    myBold = bold;
  }

  public void render(@NotNull SimpleColoredComponent renderer) {
    if (myText.isEmpty()) return;

    boolean isActive = mySelected || isAimed();
    SimpleTextAttributes linkTextAttributes = isActive ? SimpleTextAttributes.LINK_ATTRIBUTES : SimpleTextAttributes.SYNTHETIC_ATTRIBUTES;
    if (myBold) {
      linkTextAttributes = SimpleTextAttributes.merge(linkTextAttributes, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
    renderer.append(myText, linkTextAttributes, this);
  }
}
