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
package com.intellij.diagnostic.logging;

import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.openapi.ui.ComponentWithActions;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * User: anna
 * Date: 22-Mar-2006
 */
public abstract class AdditionalTabComponent extends JPanel implements ComponentContainer, ComponentWithActions {
  protected AdditionalTabComponent(LayoutManager layout) {
    super(layout);
  }

  protected AdditionalTabComponent() {
  }

  @NotNull
  public abstract String getTabTitle();

  @Nullable
  public String getTooltip() {
    return null;
  }

  @Override
  @NotNull
  public JComponent getComponent(){
    return this;
  }
}
