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
package com.intellij.find.impl.livePreview;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ReplacementView extends JPanel {
  private static final String MALFORMED_REPLACEMENT_STRING = "Malformed replacement string";

  @Override
  protected void paintComponent(@NotNull Graphics graphics) {
  }

  public ReplacementView(@Nullable String replacement) {
    String textToShow = replacement;
    if (replacement == null) {
      textToShow = MALFORMED_REPLACEMENT_STRING;
    }
    JLabel jLabel = new JLabel(textToShow);
    jLabel.setForeground(replacement != null ? new JBColor(Gray._240, Gray._200) : JBColor.RED);
    add(jLabel);
  }
}
