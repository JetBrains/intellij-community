/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.wm;

import com.intellij.ui.JBColor;

import java.awt.*;

public class AppIconScheme {

  private static Color TESTS_OK_COLOR = new Color(46, 191, 38);
  private static Color BUILD_OK_COLOR = new Color(51, 153, 255);
  private static Color INDEXING_OK_COLOR = new Color(255, 170, 0);
  private static Color ERROR_COLOR = Color.red;

  public interface Progress {

    static final Progress TESTS = new Progress() {
      public Color getOkColor() {
        return TESTS_OK_COLOR;
      }

      public Color getErrorColor() {
        return ERROR_COLOR;
      }
    };

    static final Progress BUILD = new Progress() {
      public Color getOkColor() {
        return BUILD_OK_COLOR;
      }

      public Color getErrorColor() {
        return ERROR_COLOR;
      }
    };

    static final Progress INDEXING = new Progress() {
      public Color getOkColor() {
        return INDEXING_OK_COLOR;
      }

      public Color getErrorColor() {
        return ERROR_COLOR;
      }
    };

    Color getOkColor();
    Color getErrorColor();

  }

}
