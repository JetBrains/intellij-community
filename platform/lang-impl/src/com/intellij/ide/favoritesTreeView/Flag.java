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
package com.intellij.ide.favoritesTreeView;

import java.awt.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 6/1/12
 * Time: 2:03 PM
 */
public enum Flag {
  orange(new Color(255, 128, 0)),
  blue(new Color(0, 102, 204)),
  green(new Color(0, 130, 130)),
  red(new Color(255, 45, 45)),
  brown(new Color(128, 64, 0)),
  magenta(new Color(255, 0, 255)),
  violet(new Color(128, 0, 255)),
  yellow(new Color(255, 255, 0)),
  grey(new Color(140, 140, 140));

  private final Color myColor;

  private Flag(Color color) {
    myColor = color;
  }

  public Color getColor() {
    return myColor;
  }
}
