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

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 16-Jul-2006
 * Time: 17:27:18
 */
package com.intellij.openapi.ui;

import com.intellij.xml.util.XmlStringUtil;

import javax.swing.*;
import java.awt.*;

public class PanelWithText extends JPanel {
  private final JLabel myLabel = new JLabel();

  public PanelWithText() {
    this("");
  }

  public PanelWithText(String text) {
    super(new GridBagLayout());
    //setBorder(BorderFactory.createEtchedBorder());
    myLabel.setText(XmlStringUtil.wrapInHtml(text));
    add(myLabel, new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(8,8,8,8), 0, 0));
  }

  public void setText(String text){
    myLabel.setText(XmlStringUtil.wrapInHtml(text));
  }
}
