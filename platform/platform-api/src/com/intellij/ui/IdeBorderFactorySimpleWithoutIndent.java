/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class IdeBorderFactorySimpleWithoutIndent {

  private IdeBorderFactorySimpleWithoutIndent() {
  }

  public static TitledBorder createTitledBorder(Border border,
                                                String title,
                                                int titleJustification,
                                                int titlePosition,
                                                Font titleFont,
                                                Color titleColor) {
    return IdeBorderFactory.createTitledBorderSimpleWithoutIndent(title);
  }
}
