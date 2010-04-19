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
package com.intellij.ui;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class IdeBorderFactory {
  public static TitledBorder createTitledBorder(String title) {
    //return BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title);
    return BorderFactory.createTitledBorder(new RoundedLineBorder(Color.LIGHT_GRAY, 3), title);
  }

  public static TitledBorder createTitledHeaderBorder(String title) {
    //return BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title);
    return BorderFactory.createTitledBorder(new CompoundBorder(createEmptyBorder(0, 0, 5, 5), new SideBorder(Color.LIGHT_GRAY, SideBorder.TOP)), title);
  }

  public static Border createBorder() {
    return new RoundedLineBorder(Color.GRAY, 5);
  }

  public static Border createEmptyBorder(Insets insets) {
    return new EmptyBorder(insets);
  }

  public static Border createEmptyBorder(int top, int left, int bottom, int right) {
    return new EmptyBorder(top, left, bottom, right);
  }

  public static Border createSimpleBorder() {
    return createSimpleBorder(1, 1, 1, 1);
  }

  public static Border createSimpleBorder(int top, int left, int bottom, int right) {
    return BorderFactory.createMatteBorder(top, left, bottom, right, UIUtil.getBorderSeparatorColor());
  }
}
