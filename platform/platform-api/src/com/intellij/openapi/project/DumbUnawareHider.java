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

package com.intellij.openapi.project;

import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

/**
 * @author peter
 */
public class DumbUnawareHider extends JPanel {
  @NonNls private static final String CONTENT = "content";
  @NonNls private static final String EXCUSE = "excuse";

  public DumbUnawareHider(JComponent dumbUnawareContent) {
    super(new CardLayout());
    add(dumbUnawareContent, CONTENT);
    final JBLabel label = new JBLabel("This view is not available until indices are built");
    label.setFontColor(UIUtil.FontColor.BRIGHTER);
    label.setHorizontalAlignment(SwingConstants.CENTER);
    add(label, EXCUSE);
  }

  public void setContentVisible(boolean show) {
    ((CardLayout)getLayout()).show(this, show ? CONTENT : EXCUSE);
  }
}
